package triage.testutil;

import triage.model.Application;
import triage.model.CountryRisk;
import triage.model.ProductType;

/** Builds applications that start with no risk factors; each test changes only what it is about. */
public final class AppBuilder {
    private String ref = "APP-T1";
    private ProductType product = ProductType.TERM_LIFE;
    private long sum = 100_000;
    private int age = 30;
    private boolean smoker = false;
    private double bmi = 22.0;
    private int occupation = 1;
    private boolean priorClaims = false;
    private CountryRisk country = CountryRisk.LOW;
    private boolean pep = false;
    private boolean docs = true;

    public static AppBuilder app() { return new AppBuilder(); }

    public AppBuilder ref(String v) { ref = v; return this; }
    public AppBuilder product(ProductType v) { product = v; return this; }
    public AppBuilder sum(long v) { sum = v; return this; }
    public AppBuilder age(int v) { age = v; return this; }
    public AppBuilder smoker(boolean v) { smoker = v; return this; }
    public AppBuilder bmi(double v) { bmi = v; return this; }
    public AppBuilder occupation(int v) { occupation = v; return this; }
    public AppBuilder priorClaims(boolean v) { priorClaims = v; return this; }
    public AppBuilder country(CountryRisk v) { country = v; return this; }
    public AppBuilder pep(boolean v) { pep = v; return this; }
    public AppBuilder docs(boolean v) { docs = v; return this; }

    public Application build() {
        return new Application(ref, product, sum, age, smoker, bmi, occupation, priorClaims, country, pep, docs);
    }
}
