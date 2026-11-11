package triage.model;

/** PAUSED while documents are outstanding: the clock only starts when the file is complete. */
public enum SlaStatus { PAUSED, ON_TRACK, AT_RISK, BREACHED, MET }
