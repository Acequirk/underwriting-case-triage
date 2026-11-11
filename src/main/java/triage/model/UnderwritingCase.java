package triage.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A case moving through the workflow. Only the service changes it; callers get read-only views. */
public final class UnderwritingCase {
    private final long id;
    private Application application;
    private final Instant receivedAt;
    private final List<AuditEvent> audit = new ArrayList<>();

    private CaseStatus status;
    private TriageResult triage;
    private Route route;            // effective route: the triage route unless a human overrode it
    private boolean overridden;
    private Instant slaStart;       // null until the file is complete
    private String assignee;
    private Authority assigneeAuthority;
    private Decision decision;
    private String decisionReason;
    private Instant decidedAt;

    public UnderwritingCase(long id, Application application, Instant receivedAt) {
        this.id = id;
        this.application = application;
        this.receivedAt = receivedAt;
    }

    public long id() { return id; }
    public Application application() { return application; }
    public Instant receivedAt() { return receivedAt; }
    public List<AuditEvent> audit() { return Collections.unmodifiableList(audit); }
    public CaseStatus status() { return status; }
    public TriageResult triage() { return triage; }
    public Route route() { return route; }
    public boolean overridden() { return overridden; }
    public Instant slaStart() { return slaStart; }
    public String assignee() { return assignee; }
    public Authority assigneeAuthority() { return assigneeAuthority; }
    public Decision decision() { return decision; }
    public String decisionReason() { return decisionReason; }
    public Instant decidedAt() { return decidedAt; }

    public void setApplication(Application application) { this.application = application; }
    public void record(AuditEvent event) { audit.add(event); }
    public void setStatus(CaseStatus status) { this.status = status; }
    public void setTriage(TriageResult triage) { this.triage = triage; }
    public void setRoute(Route route, boolean overridden) { this.route = route; this.overridden = overridden; }
    public void setSlaStart(Instant slaStart) { this.slaStart = slaStart; }

    public void assign(String assignee, Authority authority) {
        this.assignee = assignee;
        this.assigneeAuthority = authority;
    }

    public void clearAssignee() {
        this.assignee = null;
        this.assigneeAuthority = null;
    }

    public void decide(Decision decision, String reason, Instant at) {
        this.decision = decision;
        this.decisionReason = reason;
        this.decidedAt = at;
    }
}
