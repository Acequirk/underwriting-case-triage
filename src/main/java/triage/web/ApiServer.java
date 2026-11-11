package triage.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import triage.model.Application;
import triage.model.Authority;
import triage.model.CountryRisk;
import triage.model.Decision;
import triage.model.ProductType;
import triage.model.RiskBand;
import triage.model.Route;
import triage.rules.Rule;
import triage.service.InvalidTransitionException;
import triage.service.NotFoundException;
import triage.service.TriageService;
import triage.service.ValidationException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin HTTP adapter on the JDK's built-in server. It only translates HTTP to service calls and
 * maps errors to status codes (400 invalid input, 404 not found, 409 not allowed right now).
 * All business rules live in the service, so this layer can be swapped for Spring Boot unchanged.
 */
public final class ApiServer {
    private final TriageService service;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Gson gson = new Gson();
    private final String allowedOrigin; // null = no CORS headers

    public ApiServer(TriageService service, int port, String allowedOrigin) throws IOException {
        this.service = service;
        this.allowedOrigin = allowedOrigin;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            route(exchange);
        } catch (ValidationException e) {
            send(exchange, 400, Map.of("errors", e.errors()));
        } catch (NotFoundException e) {
            send(exchange, 404, Map.of("error", e.getMessage()));
        } catch (InvalidTransitionException e) {
            send(exchange, 409, Map.of("error", e.getMessage()));
        } catch (JsonParseException e) {
            send(exchange, 400, Map.of("error", "Request body must be valid JSON"));
        } catch (RuntimeException e) {
            send(exchange, 500, Map.of("error", "Internal server error"));
        } finally {
            exchange.close();
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        if (allowedOrigin == null) return;
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", allowedOrigin);
        headers.set("Vary", "Origin");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        headers.set("Access-Control-Max-Age", "600");
    }

    private void route(HttpExchange exchange) throws IOException {
        String[] parts = Arrays.stream(exchange.getRequestURI().getPath().split("/"))
                .filter(s -> !s.isEmpty()).toArray(String[]::new);
        String method = exchange.getRequestMethod();

        if (parts.length == 1 && parts[0].equals("health")) {
            if (allow(exchange, "GET")) send(exchange, 200, Map.of("status", "ok"));
        } else if (parts.length == 1 && parts[0].equals("rules")) {
            if (allow(exchange, "GET")) send(exchange, 200, rulesDescription());
        } else if (parts.length == 1 && parts[0].equals("queue")) {
            if (allow(exchange, "GET")) send(exchange, 200, service.queue());
        } else if (parts.length == 2 && parts[0].equals("reports") && parts[1].equals("summary")) {
            if (allow(exchange, "GET")) send(exchange, 200, service.summary());
        } else if (parts.length >= 1 && parts[0].equals("cases")) {
            routeCases(exchange, parts, method);
        } else {
            send(exchange, 404, Map.of("error", "Route not found"));
        }
    }

    private void routeCases(HttpExchange exchange, String[] parts, String method) throws IOException {
        if (parts.length == 1) {
            if (method.equals("POST")) {
                Body body = new Body(readObject(exchange));
                Application application = new Application(
                        body.string("applicantRef", true), body.choice(ProductType.class, "productType", true),
                        body.whole("sumAssured"), (int) body.whole("age"), body.bool("smoker"), body.decimal("bmi"),
                        (int) body.whole("occupationClass"), body.bool("priorClaims"),
                        body.choice(CountryRisk.class, "countryRisk", true), body.bool("pep"), body.bool("docsComplete"));
                String actor = body.string("actor", true);
                body.check();
                send(exchange, 201, service.submit(application, actor));
            } else if (method.equals("GET")) {
                Map<String, String> q = query(exchange.getRequestURI());
                send(exchange, 200, service.list(q.get("status"), q.get("route"), q.get("band")));
            } else {
                send405(exchange);
            }
        } else if (parts.length == 2) {
            if (allow(exchange, "GET")) send(exchange, 200, service.get(parseId(parts[1])));
        } else if (parts.length == 3 && method.equals("POST")) {
            long id = parseId(parts[1]);
            Body body = new Body(readObject(exchange));
            switch (parts[2]) {
                case "documents" -> {
                    String actor = body.string("actor", true);
                    body.check();
                    send(exchange, 200, service.receiveDocuments(id, actor));
                }
                case "assign" -> {
                    String assignee = body.string("assignee", true);
                    Authority authority = body.choice(Authority.class, "authority", true);
                    body.check();
                    send(exchange, 200, service.assign(id, assignee, authority));
                }
                case "decision" -> {
                    String actor = body.string("actor", true);
                    Decision decision = body.choice(Decision.class, "decision", true);
                    String reason = body.string("reason", false);
                    body.check();
                    send(exchange, 200, service.decide(id, actor, decision, reason));
                }
                case "override" -> {
                    String actor = body.string("actor", true);
                    Route route = body.choice(Route.class, "route", true);
                    String reason = body.string("reason", false);
                    body.check();
                    send(exchange, 200, service.overrideRoute(id, actor, route, reason));
                }
                default -> send(exchange, 404, Map.of("error", "Route not found"));
            }
        } else if (parts.length == 3) {
            send405(exchange);
        } else {
            send(exchange, 404, Map.of("error", "Route not found"));
        }
    }

    private Map<String, Object> rulesDescription() {
        List<Map<String, Object>> rules = service.rules().stream().map(this::describe).toList();
        List<Map<String, Object>> routes = Arrays.stream(Route.values()).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("route", r.name());
            m.put("targetHours", r.targetHours());
            return m;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rules", rules);
        out.put("bands", Map.of("MEDIUM", 25, "HIGH", 50));
        out.put("routes", routes);
        out.put("note", "Weights and thresholds are invented for this project and are not real underwriting guidelines.");
        out.put("bandOrder", Arrays.stream(RiskBand.values()).map(Enum::name).toList());
        return out;
    }

    private Map<String, Object> describe(Rule rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rule.id());
        m.put("name", rule.name());
        m.put("description", rule.description());
        m.put("points", rule.points());
        m.put("forcesSeniorReview", rule.forcesSeniorReview());
        return m;
    }

    private static long parseId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new NotFoundException("Case " + raw + " not found");
        }
    }

    private boolean allow(HttpExchange exchange, String method) throws IOException {
        if (exchange.getRequestMethod().equals(method)) return true;
        send405(exchange);
        return false;
    }

    private void send405(HttpExchange exchange) throws IOException {
        send(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private static JsonObject readObject(HttpExchange exchange) throws IOException {
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonElement element = JsonParser.parseString(raw);
        if (!element.isJsonObject()) {
            throw new ValidationException(List.of("Request body must be a JSON object"));
        }
        return element.getAsJsonObject();
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> result = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) return result;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            result.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length == 2 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
