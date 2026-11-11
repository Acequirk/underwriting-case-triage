package triage.service;

import triage.model.Application;

import java.util.ArrayList;
import java.util.List;

/** Business validation of an application's values (types are checked earlier, at the API edge). */
public final class ApplicationValidator {
    public static final long MAX_SUM_ASSURED = 10_000_000;

    private ApplicationValidator() {
    }

    public static void validate(Application a, String actor) {
        List<String> errors = new ArrayList<>();
        if (actor == null || actor.isBlank()) errors.add("actor is required");
        else if (actor.trim().length() > 40) errors.add("actor must be 40 characters or fewer");
        if (a.applicantRef() == null || a.applicantRef().isBlank()) errors.add("applicantRef is required");
        else if (a.applicantRef().trim().length() > 40) errors.add("applicantRef must be 40 characters or fewer");
        if (a.productType() == null) errors.add("productType is required");
        if (a.sumAssured() <= 0) errors.add("sumAssured must be greater than 0");
        else if (a.sumAssured() > MAX_SUM_ASSURED) errors.add("sumAssured must not exceed " + MAX_SUM_ASSURED);
        if (a.age() < 18 || a.age() > 75) errors.add("age must be between 18 and 75");
        if (!(a.bmi() >= 10 && a.bmi() <= 60)) errors.add("bmi must be between 10 and 60");
        if (a.occupationClass() < 1 || a.occupationClass() > 4) errors.add("occupationClass must be between 1 and 4");
        if (a.countryRisk() == null) errors.add("countryRisk is required");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
