package triage.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import triage.service.ValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Reads typed values from a JSON object and collects every type problem, so a client sees all of them at once. */
final class Body {
    private final JsonObject json;
    private final List<String> errors = new ArrayList<>();

    Body(JsonObject json) {
        this.json = json;
    }

    String string(String key, boolean required) {
        JsonElement e = json.get(key);
        if (isMissing(e)) {
            if (required) errors.add(key + " is required");
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            errors.add(key + " must be a string");
            return null;
        }
        return e.getAsString();
    }

    long whole(String key) {
        JsonElement e = json.get(key);
        if (isMissing(e)) {
            errors.add(key + " is required");
            return 0;
        }
        try {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new NumberFormatException();
            return new BigDecimal(e.getAsString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            errors.add(key + " must be a whole number");
            return 0;
        }
    }

    double decimal(String key) {
        JsonElement e = json.get(key);
        if (isMissing(e)) {
            errors.add(key + " is required");
            return 0;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            errors.add(key + " must be a number");
            return 0;
        }
        return e.getAsDouble();
    }

    boolean bool(String key) {
        JsonElement e = json.get(key);
        if (isMissing(e)) {
            errors.add(key + " is required");
            return false;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            errors.add(key + " must be true or false");
            return false;
        }
        return e.getAsBoolean();
    }

    <E extends Enum<E>> E choice(Class<E> type, String key, boolean required) {
        String raw = string(key, required);
        if (raw == null) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            errors.add(key + " must be one of " + java.util.Arrays.toString(type.getEnumConstants()));
            return null;
        }
    }

    /** Throws if any value read so far had a problem. */
    void check() {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static boolean isMissing(JsonElement e) {
        return e == null || e.isJsonNull();
    }
}
