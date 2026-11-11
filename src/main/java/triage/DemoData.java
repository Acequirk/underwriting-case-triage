package triage;

import triage.model.Application;
import triage.model.Authority;
import triage.model.CountryRisk;
import triage.model.Decision;
import triage.model.ProductType;
import triage.service.TriageService;

/** A handful of invented cases so the dashboard has something to show on first run. */
public final class DemoData {
    private DemoData() {
    }

    public static void load(TriageService service) {
        service.submit(new Application("APP-1001", ProductType.TERM_LIFE, 250_000, 29, false, 22.4, 1, false, CountryRisk.LOW, false, true), "intake");
        service.submit(new Application("APP-1002", ProductType.WHOLE_LIFE, 600_000, 44, true, 27.1, 2, false, CountryRisk.LOW, false, true), "intake");
        service.submit(new Application("APP-1003", ProductType.CRITICAL_ILLNESS, 1_200_000, 52, true, 33.0, 3, true, CountryRisk.MEDIUM, false, true), "intake");
        service.submit(new Application("APP-1004", ProductType.TERM_LIFE, 900_000, 38, false, 24.0, 1, false, CountryRisk.HIGH, false, true), "intake");
        service.submit(new Application("APP-1005", ProductType.HEALTH, 150_000, 33, false, 21.0, 1, false, CountryRisk.LOW, false, false), "intake");
        service.submit(new Application("APP-1006", ProductType.WHOLE_LIFE, 2_500_000, 47, false, 26.0, 2, false, CountryRisk.LOW, true, true), "intake");
        service.submit(new Application("APP-1007", ProductType.TERM_LIFE, 400_000, 31, false, 23.5, 4, true, CountryRisk.LOW, false, true), "intake");
        service.submit(new Application("APP-1008", ProductType.CRITICAL_ILLNESS, 300_000, 36, false, 25.0, 1, false, CountryRisk.LOW, false, true), "intake");

        service.assign(1, "Alex", Authority.JUNIOR);
        service.decide(1, "Alex", Decision.APPROVE, "");
        service.assign(2, "Alex", Authority.JUNIOR);
        service.assign(3, "Dana", Authority.SENIOR);
    }
}
