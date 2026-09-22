package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import project_manager.web.ProjectMutationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

final class MutationJson {
    private MutationJson() { }

    static void object(JsonNode body) {
        if (body == null || !body.isObject()) throw ProjectMutationException.badRequest("JSON object required");
    }

    static void allowed(JsonNode body, Set<String> fields) {
        body.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw ProjectMutationException.badRequest("Unknown field: " + field);
        });
    }

    static long expectedVersion(JsonNode body) {
        JsonNode value = body.get("expectedVersion");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw ProjectMutationException.invalid("expectedVersion", "Non-negative integer required");
        }
        return value.longValue();
    }

    static long expectedVersion(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]*")) {
            throw ProjectMutationException.invalid("expectedVersion", "Non-negative integer required");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw ProjectMutationException.invalid("expectedVersion", "Non-negative integer required");
        }
    }

    static String text(JsonNode value, String field, int maxLength) {
        if (value == null || !value.isTextual() || value.textValue().isBlank()
            || value.textValue().length() > maxLength) {
            throw ProjectMutationException.invalid(field, "Non-empty text of at most " + maxLength + " characters required");
        }
        return value.textValue();
    }

    static LocalDate date(JsonNode value, String field, boolean nullable) {
        if (value != null && value.isNull() && nullable) return null;
        if (value == null || !value.isTextual() || !value.textValue().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw ProjectMutationException.invalid(field, "ISO date YYYY-MM-DD required");
        }
        try {
            return LocalDate.parse(value.textValue());
        } catch (DateTimeParseException exception) {
            throw ProjectMutationException.invalid(field, "ISO date YYYY-MM-DD required");
        }
    }

    static Integer integer(JsonNode value, String field, int min, int max, boolean nullable) {
        if (value != null && value.isNull() && nullable) return null;
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
            || value.intValue() < min || value.intValue() > max) {
            throw ProjectMutationException.invalid(field, "Integer from " + min + " to " + max + " required");
        }
        return value.intValue();
    }

    static boolean bool(JsonNode value, String field) {
        if (value == null || !value.isBoolean()) {
            throw ProjectMutationException.invalid(field, "Boolean required");
        }
        return value.booleanValue();
    }
}
