package triage.service;

import triage.model.CaseStatus;
import triage.model.SlaStatus;
import triage.model.UnderwritingCase;

import java.time.Duration;
import java.time.Instant;

/** Service-level rules. The clock starts when the file is complete and the target depends on the route. */
public final class SlaPolicy {
    /** A case is at risk once 25% or less of its service window remains. */
    private static final int AT_RISK_DENOMINATOR = 4;

    private SlaPolicy() {
    }

    public record Result(SlaStatus status, Instant dueAt, Long minutesRemaining) {
    }

    public static Result evaluate(UnderwritingCase c, Instant now) {
        if (c.status() == CaseStatus.AWAITING_DOCUMENTS || c.slaStart() == null) {
            return new Result(SlaStatus.PAUSED, null, null);
        }
        Duration window = Duration.ofHours(c.route().targetHours());
        Instant due = c.slaStart().plus(window);
        if (c.status() == CaseStatus.DECIDED) {
            SlaStatus outcome = c.decidedAt().isAfter(due) ? SlaStatus.BREACHED : SlaStatus.MET;
            return new Result(outcome, due, null);
        }
        long minutes = Duration.between(now, due).toMinutes();
        if (now.isAfter(due)) {
            return new Result(SlaStatus.BREACHED, due, minutes);
        }
        long remainingMillis = Duration.between(now, due).toMillis();
        boolean atRisk = remainingMillis * AT_RISK_DENOMINATOR <= window.toMillis();
        return new Result(atRisk ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK, due, minutes);
    }
}
