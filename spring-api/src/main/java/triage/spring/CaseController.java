package triage.spring;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import triage.model.*;
import triage.rules.Rule;
import triage.service.CaseView;
import triage.service.Summary;
import triage.service.TriageService;
import triage.service.ValidationException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP adapter. No business rules here: every call delegates to the tested TriageService. */
@RestController
class CaseController {
    private final TriageService service;

    CaseController(TriageService service) {
        this.service = service;
    }

    // Request bodies. Wrapper types (Long, Integer, Boolean) let us tell "missing" from zero/false.
    record SubmitRequest(String applicantRef, ProductType productType, Long sumAssured, Integer age,
                         Boolean smoker, Double bmi, Integer occupationClass, Boolean priorClaims,
                         CountryRisk countryRisk, Boolean pep, Boolean docsComplete, String actor) {
    }

    record ActorRequest(String actor) {
    }

    record AssignRequest(String assignee, Authority authority) {
    }

    record DecisionRequest(String actor, Decision decision, String reason) {
    }

    record OverrideRequest(String actor, Route route, String reason) {
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/queue")
    List<CaseView> queue() {
        return service.queue();
    }

    @GetMapping("/reports/summary")
    Summary summary() {
        return service.summary();
    }

    @GetMapping("/cases")
    List<CaseView> list(@RequestParam(required = false) String status,
                        @RequestParam(required = false) String route,
                        @RequestParam(required = false) String band) {
        return service.list(status, route, band);
    }

    @GetMapping("/cases/{id}")
    CaseView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/cases")
    @ResponseStatus(HttpStatus.CREATED)
    CaseView submit(@RequestBody SubmitRequest r) {
        if (r.sumAssured() == null || r.age() == null || r.bmi() == null || r.occupationClass() == null
                || r.productType() == null || r.countryRisk() == null || r.applicantRef() == null || r.actor() == null) {
            throw new ValidationException(List.of(
                    "applicantRef, productType, sumAssured, age, bmi, occupationClass, countryRisk and actor are required"));
        }
        Application application = new Application(r.applicantRef(), r.productType(), r.sumAssured(), r.age(),
                Boolean.TRUE.equals(r.smoker()), r.bmi(), r.occupationClass(), Boolean.TRUE.equals(r.priorClaims()),
                r.countryRisk(), Boolean.TRUE.equals(r.pep()), Boolean.TRUE.equals(r.docsComplete()));
        return service.submit(application, r.actor());
    }

    @PostMapping("/cases/{id}/documents")
    CaseView documents(@PathVariable long id, @RequestBody ActorRequest r) {
        return service.receiveDocuments(id, r.actor());
    }

    @PostMapping("/cases/{id}/assign")
    CaseView assign(@PathVariable long id, @RequestBody AssignRequest r) {
        return service.assign(id, r.assignee(), r.authority());
    }

    @PostMapping("/cases/{id}/decision")
    CaseView decide(@PathVariable long id, @RequestBody DecisionRequest r) {
        return service.decide(id, r.actor(), r.decision(), r.reason());
    }

    @PostMapping("/cases/{id}/override")
    CaseView override(@PathVariable long id, @RequestBody OverrideRequest r) {
        return service.overrideRoute(id, r.actor(), r.route(), r.reason());
    }

    @GetMapping("/rules")
    Map<String, Object> rules() {
        List<Map<String, Object>> rules = service.rules().stream().map(this::describe).toList();
        List<Map<String, Object>> routes = Arrays.stream(Route.values()).map(route -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("route", route.name());
            m.put("targetHours", route.targetHours());
            return m;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rules", rules);
        out.put("bands", Map.of("MEDIUM", 25, "HIGH", 50));
        out.put("routes", routes);
        out.put("note", "Weights and thresholds are invented for this project and are not real underwriting guidelines.");
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
}
