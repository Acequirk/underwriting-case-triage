package triage.service;

import triage.model.Application;
import triage.model.Authority;
import triage.model.AuditEvent;
import triage.model.CaseStatus;
import triage.model.Decision;
import triage.model.RiskBand;
import triage.model.Route;
import triage.model.RuleResult;
import triage.model.TriageResult;
import triage.model.UnderwritingCase;
import triage.repository.CaseRepository;
import triage.rules.Rule;
import triage.rules.RuleBook;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The workflow. Every state change goes through here, every change is checked against the
 * business rules, and every change leaves an audit event. Methods are synchronized so two
 * requests cannot change the same case at once.
 */
public final class TriageService {
    static final int MIN_REASON_LENGTH = 10;

    private final CaseRepository repository;
    private final Clock clock;

    public TriageService(CaseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ---- commands ---------------------------------------------------------------------

    public synchronized CaseView submit(Application application, String actor) {
        ApplicationValidator.validate(application, actor);
        Instant now = clock.instant();
        String who = actor.trim();
        Application clean = new Application(application.applicantRef().trim(), application.productType(),
                application.sumAssured(), application.age(), application.smoker(), application.bmi(),
                application.occupationClass(), application.priorClaims(), application.countryRisk(),
                application.pep(), application.docsComplete());
        UnderwritingCase c = repository.create(clean, now);
        c.record(new AuditEvent(now, who, "SUBMITTED", "Application " + clean.applicantRef() + " received"));
        applyTriage(c, now, who);
        return view(c, true, now);
    }

    public synchronized CaseView receiveDocuments(long id, String actor) {
        UnderwritingCase c = require(id);
        String who = requireActor(actor);
        if (c.status() != CaseStatus.AWAITING_DOCUMENTS) {
            throw new InvalidTransitionException("Case " + id + " is not waiting for documents");
        }
        Instant now = clock.instant();
        Application a = c.application();
        c.setApplication(new Application(a.applicantRef(), a.productType(), a.sumAssured(), a.age(), a.smoker(),
                a.bmi(), a.occupationClass(), a.priorClaims(), a.countryRisk(), a.pep(), true));
        c.record(new AuditEvent(now, who, "DOCUMENTS_RECEIVED", "Documents received; the service clock starts now"));
        applyTriage(c, now, who);
        return view(c, true, now);
    }

    public synchronized CaseView assign(long id, String assignee, Authority authority) {
        UnderwritingCase c = require(id);
        String who = requireActor(assignee);
        if (authority == null) {
            throw new ValidationException("authority is required (JUNIOR or SENIOR)");
        }
        if (c.status() == CaseStatus.AWAITING_DOCUMENTS) {
            throw new InvalidTransitionException("Case " + id + " cannot be assigned while documents are outstanding");
        }
        if (c.status() == CaseStatus.DECIDED) {
            throw new InvalidTransitionException("Case " + id + " has already been decided");
        }
        if (c.route() == Route.SENIOR_REVIEW && authority != Authority.SENIOR) {
            throw new InvalidTransitionException("Case " + id + " is on senior review and needs a senior underwriter");
        }
        Instant now = clock.instant();
        c.assign(who, authority);
        c.setStatus(CaseStatus.ASSIGNED);
        c.record(new AuditEvent(now, who, "ASSIGNED", "Assigned to " + who + " (" + authority + ")"));
        return view(c, true, now);
    }

    public synchronized CaseView decide(long id, String actor, Decision decision, String reason) {
        UnderwritingCase c = require(id);
        String who = requireActor(actor);
        if (decision == null) {
            throw new ValidationException("decision is required (APPROVE, DECLINE or COUNTER_OFFER)");
        }
        if (c.status() != CaseStatus.ASSIGNED) {
            throw new InvalidTransitionException("Case " + id + " must be assigned before a decision is recorded");
        }
        if (!who.equals(c.assignee())) {
            throw new InvalidTransitionException("Only the assigned underwriter (" + c.assignee() + ") can decide case " + id);
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (decision != Decision.APPROVE && cleanReason.length() < MIN_REASON_LENGTH) {
            throw new ValidationException("a reason of at least " + MIN_REASON_LENGTH + " characters is required to "
                    + decision.name().toLowerCase().replace('_', ' '));
        }
        Instant now = clock.instant();
        c.decide(decision, cleanReason, now);
        c.setStatus(CaseStatus.DECIDED);
        c.record(new AuditEvent(now, who, "DECISION", decision + (cleanReason.isEmpty() ? "" : ": " + cleanReason)));
        return view(c, true, now);
    }

    public synchronized CaseView overrideRoute(long id, String actor, Route newRoute, String reason) {
        UnderwritingCase c = require(id);
        String who = requireActor(actor);
        if (newRoute == null || !newRoute.isReviewRoute()) {
            throw new ValidationException("route must be FAST_TRACK, STANDARD or SENIOR_REVIEW");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.length() < MIN_REASON_LENGTH) {
            throw new ValidationException("a reason of at least " + MIN_REASON_LENGTH + " characters is required to override a route");
        }
        if (c.status() != CaseStatus.TRIAGED && c.status() != CaseStatus.ASSIGNED) {
            throw new InvalidTransitionException("Case " + id + " cannot be re-routed in status " + c.status());
        }
        if (newRoute == c.route()) {
            throw new ValidationException("case is already on route " + newRoute);
        }
        TriageResult triage = c.triage();
        if (triage.forcedSeniorReview() && newRoute != Route.SENIOR_REVIEW) {
            String hardRules = triage.firedRules().stream().filter(RuleResult::forcesSeniorReview)
                    .map(RuleResult::ruleId).collect(Collectors.joining(", "));
            throw new InvalidTransitionException("Case " + id + " must stay on senior review: hard rule(s) " + hardRules
                    + " cannot be overridden");
        }
        Instant now = clock.instant();
        Route old = c.route();
        c.setRoute(newRoute, newRoute != triage.route());
        c.record(new AuditEvent(now, who, "OVERRIDE", "Route " + old + " -> " + newRoute + ": " + cleanReason));
        if (c.status() == CaseStatus.ASSIGNED && newRoute == Route.SENIOR_REVIEW
                && c.assigneeAuthority() != Authority.SENIOR) {
            String previous = c.assignee();
            c.clearAssignee();
            c.setStatus(CaseStatus.TRIAGED);
            c.record(new AuditEvent(now, who, "UNASSIGNED", previous + " removed: senior review needs a senior underwriter"));
        }
        return view(c, true, now);
    }

    // ---- queries ----------------------------------------------------------------------

    public synchronized CaseView get(long id) {
        return view(require(id), true, clock.instant());
    }

    public synchronized List<CaseView> list(String status, String route, String band) {
        CaseStatus statusFilter = parse(CaseStatus.class, status, "status");
        Route routeFilter = parse(Route.class, route, "route");
        RiskBand bandFilter = parse(RiskBand.class, band, "band");
        Instant now = clock.instant();
        return repository.findAll().stream()
                .filter(c -> statusFilter == null || c.status() == statusFilter)
                .filter(c -> routeFilter == null || c.route() == routeFilter)
                .filter(c -> bandFilter == null || c.triage().band() == bandFilter)
                .map(c -> view(c, false, now))
                .toList();
    }

    /** Open work, most urgent first: earliest due time, then higher score, then oldest case. */
    public synchronized List<CaseView> queue() {
        Instant now = clock.instant();
        return repository.findAll().stream()
                .filter(c -> c.status() == CaseStatus.TRIAGED || c.status() == CaseStatus.ASSIGNED)
                .map(c -> view(c, false, now))
                .sorted(Comparator.comparing((CaseView v) -> java.time.Instant.parse(v.dueAt()))
                        .thenComparing(CaseView::score, Comparator.reverseOrder())
                        .thenComparing(CaseView::id))
                .toList();
    }

    public synchronized Summary summary() {
        Instant now = clock.instant();
        List<CaseView> all = repository.findAll().stream().map(c -> view(c, false, now)).toList();
        Map<String, Integer> byBand = new LinkedHashMap<>();
        for (RiskBand b : RiskBand.values()) byBand.put(b.name(), 0);
        Map<String, Integer> byRoute = new LinkedHashMap<>();
        for (Route r : Route.values()) byRoute.put(r.name(), 0);
        Map<String, Integer> decisions = new LinkedHashMap<>();
        for (Decision d : Decision.values()) decisions.put(d.name(), 0);
        int awaiting = 0, open = 0, decided = 0, atRisk = 0, breached = 0;
        for (CaseView v : all) {
            byBand.merge(v.band(), 1, Integer::sum);
            byRoute.merge(v.route(), 1, Integer::sum);
            switch (CaseStatus.valueOf(v.status())) {
                case AWAITING_DOCUMENTS -> awaiting++;
                case TRIAGED, ASSIGNED -> open++;
                case DECIDED -> decided++;
            }
            if (v.decision() != null) decisions.merge(v.decision(), 1, Integer::sum);
            if ("AT_RISK".equals(v.slaStatus())) atRisk++;
            if ("BREACHED".equals(v.slaStatus())) breached++;
        }
        return new Summary(all.size(), awaiting, open, decided, atRisk, breached, byBand, byRoute, decisions);
    }

    public List<Rule> rules() {
        return RuleBook.rules();
    }

    // ---- helpers ----------------------------------------------------------------------

    private void applyTriage(UnderwritingCase c, Instant now, String actor) {
        TriageResult result = RuleBook.evaluate(c.application());
        c.setTriage(result);
        c.setRoute(result.route(), false);
        if (result.route() == Route.AWAITING_DOCUMENTS) {
            c.setStatus(CaseStatus.AWAITING_DOCUMENTS);
            c.setSlaStart(null);
        } else {
            c.setStatus(CaseStatus.TRIAGED);
            c.setSlaStart(now);
        }
        c.record(new AuditEvent(now, "triage-engine", "TRIAGED", result.rationale()));
    }

    private UnderwritingCase require(long id) {
        return repository.find(id).orElseThrow(() -> new NotFoundException("Case " + id + " not found"));
    }

    private static String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new ValidationException("actor is required");
        }
        if (actor.trim().length() > 40) {
            throw new ValidationException("actor must be 40 characters or fewer");
        }
        return actor.trim();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(field + " must be one of " + java.util.Arrays.toString(type.getEnumConstants()));
        }
    }

    private CaseView view(UnderwritingCase c, boolean withAudit, Instant now) {
        Application a = c.application();
        TriageResult t = c.triage();
        SlaPolicy.Result sla = SlaPolicy.evaluate(c, now);
        List<CaseView.AuditView> audit = withAudit
                ? c.audit().stream().map(e -> new CaseView.AuditView(e.at().toString(), e.actor(), e.action(), e.detail())).toList()
                : List.of();
        return new CaseView(c.id(), a.applicantRef(), a.productType().name(), a.sumAssured(), a.age(), a.smoker(),
                a.bmi(), a.occupationClass(), a.priorClaims(), a.countryRisk().name(), a.pep(),
                c.status().name(), c.route().name(), t.route().name(), c.overridden(), t.score(), t.band().name(),
                t.forcedSeniorReview(), t.firedRules(), t.rationale(),
                c.assignee(), c.assigneeAuthority() == null ? null : c.assigneeAuthority().name(),
                c.decision() == null ? null : c.decision().name(), c.decisionReason(),
                c.receivedAt().toString(), sla.dueAt() == null ? null : sla.dueAt().toString(),
                sla.status().name(), sla.minutesRemaining(), audit);
    }
}
