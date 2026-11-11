package triage.spring;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import triage.service.InvalidTransitionException;
import triage.service.NotFoundException;
import triage.service.ValidationException;

import java.util.List;
import java.util.Map;

/** Same status codes and JSON shapes as the JDK adapter, so the React app does not change. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<Map<String, List<String>>> invalid(ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("errors", e.errors()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<Map<String, String>> conflict(InvalidTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    // Bad JSON, or a value that cannot be converted (e.g. an unknown enum name).
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is not valid for this endpoint"));
    }
}
