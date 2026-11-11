package triage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import triage.repository.InMemoryCaseRepository;
import triage.service.TriageService;
import triage.testutil.MutableClock;
import triage.web.ApiServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Exercises the real HTTP server over a real socket. */
public class ApiServerTest {
    private static final String ORIGIN = "http://localhost:5173";
    private final HttpClient client = HttpClient.newHttpClient();
    private ApiServer server;
    private MutableClock clock;
    private String base;

    private static final String LOW_APP = """
            {"applicantRef":"APP-1","productType":"TERM_LIFE","sumAssured":100000,"age":30,"smoker":false,
             "bmi":22.0,"occupationClass":1,"priorClaims":false,"countryRisk":"LOW","pep":false,
             "docsComplete":true,"actor":"intake"}""";

    @Before
    public void start() throws Exception {
        clock = new MutableClock(Instant.parse("2026-11-02T01:00:00Z"));
        server = new ApiServer(new TriageService(new InMemoryCaseRepository(), clock), 0, ORIGIN);
        server.start();
        base = "http://localhost:" + server.port();
    }

    @After
    public void stop() {
        server.stop();
    }

    private record Reply(int status, String body, HttpResponse<String> raw) {
        JsonObject json() { return JsonParser.parseString(body).getAsJsonObject(); }
        JsonArray array() { return JsonParser.parseString(body).getAsJsonArray(); }
    }

    private Reply call(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(), r.body(), r);
    }

    private Reply get(String path) throws Exception { return call("GET", path, null); }
    private Reply post(String path, String body) throws Exception { return call("POST", path, body); }

    @Test
    public void healthAndUnknownRoute() throws Exception {
        assertEquals("ok", get("/health").json().get("status").getAsString());
        assertEquals(404, get("/nope").status());
        assertEquals(405, post("/health", "{}").status());
    }

    @Test
    public void fullWorkflowOverHttp() throws Exception {
        Reply created = post("/cases", LOW_APP);
        assertEquals(201, created.status());
        assertEquals("FAST_TRACK", created.json().get("route").getAsString());
        assertEquals("LOW", created.json().get("band").getAsString());
        assertEquals(2, created.json().getAsJsonArray("audit").size());

        assertEquals(409, post("/cases/1/decision", "{\"actor\":\"Alex\",\"decision\":\"APPROVE\"}").status());
        assertEquals(200, post("/cases/1/assign", "{\"assignee\":\"Alex\",\"authority\":\"JUNIOR\"}").status());
        Reply decided = post("/cases/1/decision", "{\"actor\":\"Alex\",\"decision\":\"APPROVE\"}");
        assertEquals(200, decided.status());
        assertEquals("DECIDED", decided.json().get("status").getAsString());
        assertEquals("MET", decided.json().get("slaStatus").getAsString());

        Reply fetched = get("/cases/1");
        assertEquals(4, fetched.json().getAsJsonArray("audit").size());
    }

    @Test
    public void documentsOverrideAndFiltersOverHttp() throws Exception {
        post("/cases", LOW_APP.replace("\"docsComplete\":true", "\"docsComplete\":false"));
        post("/cases", LOW_APP);
        assertEquals(1, get("/cases?status=AWAITING_DOCUMENTS").array().size());
        assertEquals(1, get("/cases?route=FAST_TRACK").array().size());
        assertEquals(2, get("/cases").array().size());
        assertEquals(1, get("/queue").array().size());

        assertEquals(200, post("/cases/1/documents", "{\"actor\":\"ops\"}").status());
        assertEquals(2, get("/queue").array().size());
        assertEquals(409, post("/cases/1/documents", "{\"actor\":\"ops\"}").status());

        Reply override = post("/cases/2/override", "{\"actor\":\"Dana\",\"route\":\"STANDARD\",\"reason\":\"Requested by compliance\"}");
        assertEquals(200, override.status());
        assertTrue(override.json().get("overridden").getAsBoolean());
    }

    @Test
    public void hardRuleOverrideIsRefusedWith409() throws Exception {
        post("/cases", LOW_APP.replace("\"pep\":false", "\"pep\":true"));
        Reply r = post("/cases/1/override", "{\"actor\":\"Dana\",\"route\":\"STANDARD\",\"reason\":\"Looks fine to me\"}");
        assertEquals(409, r.status());
        assertTrue(r.json().get("error").getAsString().contains("R10"));
    }

    @Test
    public void seniorReviewAssignmentByJuniorIsRefusedWith409() throws Exception {
        post("/cases", LOW_APP.replace("\"pep\":false", "\"pep\":true"));
        assertEquals(409, post("/cases/1/assign", "{\"assignee\":\"Alex\",\"authority\":\"JUNIOR\"}").status());
        assertEquals(200, post("/cases/1/assign", "{\"assignee\":\"Dana\",\"authority\":\"SENIOR\"}").status());
    }

    @Test
    public void validationErrorsAreAllReportedTogether() throws Exception {
        Reply r = post("/cases", "{\"applicantRef\":\"\",\"productType\":\"CAR\",\"sumAssured\":\"lots\",\"age\":17.5,"
                + "\"smoker\":\"no\",\"bmi\":\"x\",\"occupationClass\":9,\"priorClaims\":false,\"countryRisk\":\"LOW\","
                + "\"pep\":false,\"docsComplete\":true,\"actor\":\"intake\"}");
        assertEquals(400, r.status());
        String errors = r.json().getAsJsonArray("errors").toString();
        assertTrue(errors, errors.contains("productType must be one of"));
        assertTrue(errors, errors.contains("sumAssured must be a whole number"));
        assertTrue(errors, errors.contains("age must be a whole number"));
        assertTrue(errors, errors.contains("smoker must be true or false"));
        assertTrue(errors, errors.contains("bmi must be a number"));
        assertTrue(r.json().getAsJsonArray("errors").size() >= 5);
    }

    @Test
    public void missingFieldsAreReported() throws Exception {
        Reply r = post("/cases", "{}");
        assertEquals(400, r.status());
        String errors = r.json().getAsJsonArray("errors").toString();
        assertTrue(errors.contains("applicantRef is required"));
        assertTrue(errors.contains("docsComplete is required"));
        assertTrue(errors.contains("actor is required"));
    }

    @Test
    public void businessValidationReturns400() throws Exception {
        Reply r = post("/cases", LOW_APP.replace("\"age\":30", "\"age\":12"));
        assertEquals(400, r.status());
        assertTrue(r.json().getAsJsonArray("errors").toString().contains("age must be between 18 and 75"));
    }

    @Test
    public void badJsonAndNonObjectBodiesAre400() throws Exception {
        assertEquals(400, post("/cases", "{not json").status());
        assertEquals(400, post("/cases", "[1,2]").status());
    }

    @Test
    public void unknownCaseAndBadIdAre404() throws Exception {
        assertEquals(404, get("/cases/99").status());
        assertEquals(404, get("/cases/abc").status());
        assertEquals(404, post("/cases/99/assign", "{\"assignee\":\"A\",\"authority\":\"JUNIOR\"}").status());
    }

    @Test
    public void unknownActionAndWrongMethodsAreRejected() throws Exception {
        post("/cases", LOW_APP);
        assertEquals(404, post("/cases/1/explode", "{}").status());
        assertEquals(405, call("DELETE", "/cases", null).status());
        assertEquals(405, get("/cases/1/assign").status());
        assertEquals(405, post("/queue", "{}").status());
    }

    @Test
    public void invalidFilterIs400() throws Exception {
        assertEquals(400, get("/cases?status=nope").status());
    }

    @Test
    public void rulesEndpointExplainsTheScoring() throws Exception {
        JsonObject rules = get("/rules").json();
        assertEquals(12, rules.getAsJsonArray("rules").size());
        assertEquals(25, rules.getAsJsonObject("bands").get("MEDIUM").getAsInt());
        assertTrue(rules.get("note").getAsString().contains("invented"));
    }

    @Test
    public void summaryOverHttp() throws Exception {
        post("/cases", LOW_APP);
        JsonObject s = get("/reports/summary").json();
        assertEquals(1, s.get("total").getAsInt());
        assertEquals(1, s.get("open").getAsInt());
    }

    @Test
    public void corsHeadersAndPreflight() throws Exception {
        Reply r = get("/health");
        assertEquals(ORIGIN, r.raw().headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        Reply preflight = call("OPTIONS", "/cases", null);
        assertEquals(204, preflight.status());
        assertEquals("", preflight.body());
        assertTrue(preflight.raw().headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));
    }

    @Test
    public void noCorsHeadersWhenNoOriginConfigured() throws Exception {
        ApiServer open = new ApiServer(new TriageService(new InMemoryCaseRepository(), clock), 0, null);
        open.start();
        try {
            HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + open.port() + "/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertNull(r.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        } finally {
            open.stop();
        }
    }

    @Test
    public void concurrentSubmissionsGetUniqueIds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            futures.add(pool.submit(() -> post("/cases", LOW_APP).json().get("id").getAsInt()));
        }
        Set<Integer> ids = new HashSet<>();
        for (Future<Integer> f : futures) ids.add(f.get());
        pool.shutdown();
        assertEquals(40, ids.size());
        assertEquals(40, get("/cases").array().size());
    }

    @Test
    public void dueTimeAndMinutesRemainingAreExposed() throws Exception {
        post("/cases", LOW_APP);
        clock.advance(Duration.ofHours(2));
        JsonObject c = get("/cases/1").json();
        assertEquals("2026-11-03T01:00:00Z", c.get("dueAt").getAsString());
        assertEquals(22 * 60, c.get("minutesRemaining").getAsInt());
    }
}
