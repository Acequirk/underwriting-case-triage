package triage.service;

import java.util.Map;

public record Summary(
        int total,
        int awaitingDocuments,
        int open,
        int decided,
        int atRisk,
        int breached,
        Map<String, Integer> byBand,
        Map<String, Integer> byRoute,
        Map<String, Integer> decisions) {
}
