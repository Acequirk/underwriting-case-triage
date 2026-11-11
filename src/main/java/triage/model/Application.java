package triage.model;

/** The facts about a (synthetic) insurance application that the rules look at. */
public record Application(
        String applicantRef,
        ProductType productType,
        long sumAssured,
        int age,
        boolean smoker,
        double bmi,
        int occupationClass,
        boolean priorClaims,
        CountryRisk countryRisk,
        boolean pep,
        boolean docsComplete) {
}
