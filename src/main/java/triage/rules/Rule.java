package triage.rules;

import triage.model.Application;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One explainable scoring rule. Every rule states how many points it adds and why, so a
 * reviewer can see exactly what drove a score. A rule that forcesSeniorReview is a hard
 * rule: no score, however low, may route the case below senior review.
 */
public record Rule(
        String id,
        String name,
        String description,
        int points,
        boolean forcesSeniorReview,
        Predicate<Application> applies,
        Function<Application, String> reason) {
}
