package triage.model;

import java.time.Instant;

/** One append-only entry in a case's history. */
public record AuditEvent(Instant at, String actor, String action, String detail) {
}
