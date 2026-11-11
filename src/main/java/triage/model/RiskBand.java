package triage.model;

public enum RiskBand {
    LOW, MEDIUM, HIGH;

    /** Score thresholds: below 25 is low, 25-49 medium, 50 and above high. */
    public static RiskBand fromScore(int score) {
        if (score >= 50) return HIGH;
        if (score >= 25) return MEDIUM;
        return LOW;
    }
}
