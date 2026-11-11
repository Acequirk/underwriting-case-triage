package triage;

import org.junit.Test;
import triage.model.Application;
import triage.model.CountryRisk;
import triage.model.ProductType;
import triage.model.RiskBand;
import triage.model.Route;
import triage.model.RuleResult;
import triage.model.TriageResult;
import triage.rules.RuleBook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static triage.testutil.AppBuilder.app;

public class RuleBookTest {

    private static int score(Application a) {
        return RuleBook.evaluate(a).score();
    }

    private static boolean fired(Application a, String ruleId) {
        return RuleBook.evaluate(a).firedRules().stream().anyMatch(r -> r.ruleId().equals(ruleId));
    }

    @Test
    public void applicantWithNoRiskFactorsScoresZeroAndIsFastTracked() {
        TriageResult r = RuleBook.evaluate(app().build());
        assertEquals(0, r.score());
        assertEquals(RiskBand.LOW, r.band());
        assertEquals(Route.FAST_TRACK, r.route());
        assertTrue(r.firedRules().isEmpty());
        assertTrue(r.rationale().contains("No rules fired"));
    }

    @Test
    public void ageBoundaries() {
        assertEquals(0, score(app().age(39).build()));
        assertEquals(8, score(app().age(40).build()));
        assertEquals(8, score(app().age(49).build()));
        assertEquals(15, score(app().age(50).build()));
        assertTrue(fired(app().age(49).build(), "R02"));
        assertFalse(fired(app().age(50).build(), "R02"));
    }

    @Test
    public void bmiBoundaries() {
        assertEquals(15, score(app().bmi(16.9).build()));
        assertEquals(0, score(app().bmi(17.0).build()));
        assertEquals(0, score(app().bmi(31.9).build()));
        assertEquals(15, score(app().bmi(32.0).build()));
    }

    @Test
    public void coverAmountBoundaries() {
        assertEquals(0, score(app().sum(499_999).build()));
        assertEquals(8, score(app().sum(500_000).build()));
        assertEquals(8, score(app().sum(999_999).build()));
        assertEquals(15, score(app().sum(1_000_000).build()));
        assertEquals(15, score(app().sum(1_999_999).build()));
        assertEquals(25, score(app().sum(2_000_000).build()));   // R05 15 + R11 10
    }

    @Test
    public void smokerOccupationAndClaims() {
        assertEquals(20, score(app().smoker(true).build()));
        assertEquals(0, score(app().occupation(2).build()));
        assertEquals(10, score(app().occupation(3).build()));
        assertEquals(10, score(app().occupation(4).build()));
        assertEquals(10, score(app().priorClaims(true).build()));
    }

    @Test
    public void criticalIllnessRuleNeedsBothProductAndAge() {
        assertEquals(8 + 5, score(app().product(ProductType.CRITICAL_ILLNESS).age(45).build()));   // R02 + R12
        assertFalse(fired(app().product(ProductType.CRITICAL_ILLNESS).age(44).build(), "R12"));
        assertFalse(fired(app().product(ProductType.TERM_LIFE).age(45).build(), "R12"));
    }

    @Test
    public void bandThresholds() {
        assertEquals(RiskBand.LOW, RiskBand.fromScore(24));
        assertEquals(RiskBand.MEDIUM, RiskBand.fromScore(25));
        assertEquals(RiskBand.MEDIUM, RiskBand.fromScore(49));
        assertEquals(RiskBand.HIGH, RiskBand.fromScore(50));
    }

    @Test
    public void routesFollowBands() {
        assertEquals(Route.FAST_TRACK, RuleBook.evaluate(app().smoker(false).age(45).build()).route());       // 8
        assertEquals(Route.STANDARD, RuleBook.evaluate(app().smoker(true).age(45).build()).route());        // 28
        assertEquals(Route.SENIOR_REVIEW, RuleBook.evaluate(app().smoker(true).age(55).bmi(35).build()).route()); // 50
    }

    @Test
    public void hardRulesForceSeniorReviewEvenWhenScoreIsNotHigh() {
        TriageResult pep = RuleBook.evaluate(app().pep(true).build());
        assertEquals(30, pep.score());
        assertEquals(RiskBand.MEDIUM, pep.band());
        assertEquals(Route.SENIOR_REVIEW, pep.route());
        assertTrue(pep.forcedSeniorReview());

        TriageResult country = RuleBook.evaluate(app().country(CountryRisk.HIGH).build());
        assertEquals(Route.SENIOR_REVIEW, country.route());
        assertTrue(country.forcedSeniorReview());

        TriageResult cover = RuleBook.evaluate(app().sum(2_000_000).build());
        assertEquals(Route.SENIOR_REVIEW, cover.route());
        assertTrue(cover.forcedSeniorReview());
    }

    @Test
    public void mediumCountryRiskDoesNotScore() {
        assertEquals(0, score(app().country(CountryRisk.MEDIUM).build()));
    }

    @Test
    public void missingDocumentsHoldTheCaseButStillScoreIt() {
        TriageResult r = RuleBook.evaluate(app().smoker(true).docs(false).build());
        assertEquals(Route.AWAITING_DOCUMENTS, r.route());
        assertEquals(20, r.score());
        assertTrue(r.rationale().contains("clock has not started"));
    }

    @Test
    public void scoreEqualsSumOfFiredRulePoints() {
        Application a = app().age(52).smoker(true).bmi(33).sum(1_500_000).occupation(3).priorClaims(true)
                .product(ProductType.CRITICAL_ILLNESS).build();
        TriageResult r = RuleBook.evaluate(a);
        int sum = r.firedRules().stream().mapToInt(RuleResult::points).sum();
        assertEquals(sum, r.score());
        assertEquals(15 + 20 + 15 + 15 + 10 + 10 + 5, r.score());
    }

    @Test
    public void rationaleNamesEveryFiredRuleWithItsPoints() {
        TriageResult r = RuleBook.evaluate(app().smoker(true).age(41).build());
        assertTrue(r.rationale().contains("applicant is a smoker (+20)"));
        assertTrue(r.rationale().contains("age 41 is between 40 and 49 (+8)"));
        assertTrue(r.rationale().contains("Score 28"));
    }

    @Test
    public void evaluationIsDeterministic() {
        Application a = app().age(61).smoker(true).sum(750_000).build();
        assertEquals(RuleBook.evaluate(a), RuleBook.evaluate(a));
    }

    @Test
    public void ruleIdsAreUnique() {
        long distinct = RuleBook.rules().stream().map(r -> r.id()).distinct().count();
        assertEquals(RuleBook.rules().size(), distinct);
    }
}
