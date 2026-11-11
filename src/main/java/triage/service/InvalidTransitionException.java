package triage.service;

/** A request that is well-formed but not allowed in the case's current state or by a business rule. */
public class InvalidTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidTransitionException(String message) {
        super(message);
    }
}
