package triage.model;

import java.util.List;

public record TriageResult(
        int score,
        RiskBand band,
        Route route,
        boolean forcedSeniorReview,
        List<RuleResult> firedRules,
        String rationale) {
}
