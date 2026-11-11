package triage.service;

import java.util.List;

public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient List<String> errors;

    public ValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public ValidationException(String error) {
        this(List.of(error));
    }

    public List<String> errors() {
        return errors;
    }
}
