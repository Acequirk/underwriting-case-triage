package triage.rules;

import triage.model.Application;
import triage.model.CountryRisk;
import triage.model.ProductType;
import triage.model.RiskBand;
import triage.model.Route;
import triage.model.RuleResult;
import triage.model.TriageResult;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The scoring rules and the triage decision. Pure functions: the same application always
 * produces the same result, which is what makes the engine easy to test and to audit.
 *
 * All thresholds and weights here are invented for this portfolio project. They are not
 * taken from any real insurer's underwriting guidelines.
 */
public final class RuleBook {
    public static final long MID_COVER = 500_000;
    public static final long HIGH_COVER = 1_000_000;
    public static final long VERY_HIGH_COVER = 2_000_000;

    private static final List<Rule> RULES = List.of(
            new Rule("R01", "Age 50 or above", "Applicants aged 50 or over score higher.", 15, false,
                    a -> a.age() >= 50,
                    a -> "age " + a.age() + " is 50 or above"),
            new Rule("R02", "Age 40 to 49", "Applicants aged 40 to 49 score slightly higher.", 8, false,
                    a -> a.age() >= 40 && a.age() < 50,
                    a -> "age " + a.age() + " is between 40 and 49"),
            new Rule("R03", "Smoker", "Smokers score higher.", 20, false,
                    Application::smoker,
                    a -> "applicant is a smoker"),
            new Rule("R04", "BMI outside normal range", "BMI of 32 or more, or below 17.", 15, false,
                    a -> a.bmi() >= 32 || a.bmi() < 17,
                    a -> String.format(Locale.ROOT, "BMI %.1f is outside the 17 to 32 range", a.bmi())),
            new Rule("R05", "High cover amount", "Sum assured of 1,000,000 or more.", 15, false,
                    a -> a.sumAssured() >= HIGH_COVER,
                    a -> "sum assured " + money(a.sumAssured()) + " is 1,000,000 or more"),
            new Rule("R06", "Medium cover amount", "Sum assured from 500,000 up to 999,999.", 8, false,
                    a -> a.sumAssured() >= MID_COVER && a.sumAssured() < HIGH_COVER,
                    a -> "sum assured " + money(a.sumAssured()) + " is between 500,000 and 999,999"),
            new Rule("R07", "Hazardous occupation", "Occupation class 3 or 4.", 10, false,
                    a -> a.occupationClass() >= 3,
                    a -> "occupation class " + a.occupationClass() + " is 3 or higher"),
            new Rule("R08", "Prior claims", "Applicant has made prior claims.", 10, false,
                    Application::priorClaims,
                    a -> "applicant has prior claims"),
            new Rule("R09", "High-risk country", "Country risk rated high. Hard rule: senior review.", 25, true,
                    a -> a.countryRisk() == CountryRisk.HIGH,
                    a -> "country risk is rated HIGH"),
            new Rule("R10", "Politically exposed person", "PEP flag set. Hard rule: senior review.", 30, true,
                    Application::pep,
                    a -> "applicant is flagged as a politically exposed person"),
            new Rule("R11", "Very high cover amount", "Sum assured of 2,000,000 or more. Hard rule: senior review.", 10, true,
                    a -> a.sumAssured() >= VERY_HIGH_COVER,
                    a -> "sum assured " + money(a.sumAssured()) + " is 2,000,000 or more"),
            new Rule("R12", "Critical illness at 45 or above", "Critical illness cover for applicants aged 45 or over.", 5, false,
                    a -> a.productType() == ProductType.CRITICAL_ILLNESS && a.age() >= 45,
                    a -> "critical illness cover at age " + a.age())
    );

    private RuleBook() {
    }

    public static List<Rule> rules() {
        return RULES;
    }

    /** Scores an application, picks the review route and explains why. */
    public static TriageResult evaluate(Application application) {
        List<RuleResult> fired = new ArrayList<>();
        int score = 0;
        boolean forced = false;
        for (Rule rule : RULES) {
            if (rule.applies().test(application)) {
                fired.add(new RuleResult(rule.id(), rule.name(), rule.points(), rule.reason().apply(application),
                        rule.forcesSeniorReview()));
                score += rule.points();
                forced |= rule.forcesSeniorReview();
            }
        }
        RiskBand band = RiskBand.fromScore(score);
        Route route = chooseRoute(application, band, forced);
        return new TriageResult(score, band, route, forced, List.copyOf(fired),
                rationale(score, band, route, forced, fired));
    }

    private static Route chooseRoute(Application application, RiskBand band, boolean forced) {
        if (!application.docsComplete()) {
            return Route.AWAITING_DOCUMENTS;
        }
        if (forced || band == RiskBand.HIGH) {
            return Route.SENIOR_REVIEW;
        }
        return band == RiskBand.MEDIUM ? Route.STANDARD : Route.FAST_TRACK;
    }

    private static String rationale(int score, RiskBand band, Route route, boolean forced, List<RuleResult> fired) {
        StringBuilder text = new StringBuilder();
        text.append("Score ").append(score).append(" (").append(band).append(" risk). ");
        if (fired.isEmpty()) {
            text.append("No rules fired. ");
        } else {
            text.append("Driven by: ");
            for (int i = 0; i < fired.size(); i++) {
                if (i > 0) text.append("; ");
                text.append(fired.get(i).reason()).append(" (+").append(fired.get(i).points()).append(")");
            }
            text.append(". ");
        }
        switch (route) {
            case AWAITING_DOCUMENTS -> text.append("Held until documents are received; the service clock has not started.");
            case SENIOR_REVIEW -> text.append(forced
                    ? "Routed to senior review because a hard rule applies."
                    : "Routed to senior review because the score is in the high band.");
            case STANDARD -> text.append("Routed to standard review.");
            case FAST_TRACK -> text.append("Eligible for fast-track review.");
        }
        return text.toString();
    }

    private static String money(long amount) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(amount);
    }
}
