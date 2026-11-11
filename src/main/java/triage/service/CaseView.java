package triage.service;

import triage.model.RuleResult;

import java.util.List;

/** Read-only snapshot of a case for the API. Times are ISO-8601 strings. */
public record CaseView(
        long id,
        String applicantRef,
        String productType,
        long sumAssured,
        int age,
        boolean smoker,
        double bmi,
        int occupationClass,
        boolean priorClaims,
        String countryRisk,
        boolean pep,
        String status,
        String route,
        String triageRoute,
        boolean overridden,
        int score,
        String band,
        boolean forcedSeniorReview,
        List<RuleResult> firedRules,
        String rationale,
        String assignee,
        String assigneeAuthority,
        String decision,
        String decisionReason,
        String receivedAt,
        String dueAt,
        String slaStatus,
        Long minutesRemaining,
        List<AuditView> audit) {

    public record AuditView(String at, String actor, String action, String detail) {
    }
}
