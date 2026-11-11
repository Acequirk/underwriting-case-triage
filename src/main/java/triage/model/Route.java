package triage.model;

/**
 * Where a case goes after triage. scrutiny orders the routes from least to most review;
 * targetHours is the service target measured from the moment the file is complete.
 */
public enum Route {
    AWAITING_DOCUMENTS(0, 0),
    FAST_TRACK(1, 24),
    STANDARD(2, 48),
    SENIOR_REVIEW(3, 72);

    private final int scrutiny;
    private final int targetHours;

    Route(int scrutiny, int targetHours) {
        this.scrutiny = scrutiny;
        this.targetHours = targetHours;
    }

    public int scrutiny() { return scrutiny; }

    public int targetHours() { return targetHours; }

    /** Routes a human may select when overriding. */
    public boolean isReviewRoute() { return this != AWAITING_DOCUMENTS; }
}
