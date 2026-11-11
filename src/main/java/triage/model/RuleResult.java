package triage.model;

/** A rule that fired, with the points it added and the plain-language reason. */
public record RuleResult(String ruleId, String name, int points, String reason, boolean forcesSeniorReview) {
}
