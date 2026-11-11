package triage;

import triage.repository.InMemoryCaseRepository;
import triage.service.TriageService;
import triage.web.ApiServer;

import java.time.Clock;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String origin = System.getenv().getOrDefault("ALLOWED_ORIGIN", "http://localhost:5173");
        TriageService service = new TriageService(new InMemoryCaseRepository(), Clock.systemUTC());
        if (!"false".equalsIgnoreCase(System.getenv().getOrDefault("SEED_DEMO", "true"))) {
            DemoData.load(service);
        }
        ApiServer server = new ApiServer(service, port, origin);
        server.start();
        System.out.println("Underwriting triage API listening on http://localhost:" + server.port()
                + " (CORS origin " + origin + ", demo data " + (System.getenv("SEED_DEMO") == null ? "on" : System.getenv("SEED_DEMO")) + ")");
    }
}
