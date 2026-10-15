package project_manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import project_manager.web.ArchitectureMutationException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Closed contract for passport writes, independent of the legacy JSON validator. */
public record ArchitecturePassportInput(long expectedVersion, Map<String, Object> changes) {
    private static final List<String> FIELDS = List.of("criticality", "stage", "solutionType", "architect", "solutionSummary",
        "availabilityRequirement", "recoveryRequirement", "lastReviewDate", "dataClasses", "environments");
    static final Set<String> CRITICALITY = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STAGE = Set.of("NOT_STARTED", "IN_DESIGN", "IN_REVIEW", "APPROVED", "CHANGES_REQUIRED");
    private static final Set<String> SOLUTION = Set.of("NEW_SOLUTION", "EXISTING_SOLUTION_CHANGE", "INTEGRATION", "MIGRATION");
    static final Set<String> DATA = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "PERSONAL", "PAYMENT", "COMMERCIAL_SECRET");
    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "PREPROD", "PROD", "OTHER");
    private static final Pattern UNSAFE = Pattern.compile(
        "[<>`]|\\$\\(|(?i:&(?:lt|gt);|javascript:|vbscript:|data:|\\bon[a-z]+\\s*=|\\b(?:eval|exec|fetch|alert|prompt|setTimeout)\\s*\\(|\\bfunction\\s*(?:\\w+\\s*)?\\(|\\b(?:document|window)\\.)"
        + "|(?i:[a-z][a-z0-9+.-]*://|\\b(?:https?:|mailto:|jdbc:|mongodb:|\\bwww\\.|localhost\\b))"
        + "|(?i:\\b[a-z0-9-]+\\.(?:com|org|net|ru|io|internal|local)\\b)"
        + "|(?<![\\p{L}\\d])(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)"
        + "|(?i:(?<![a-f0-9])(?:[a-f0-9]{1,4}:){7}[a-f0-9]{1,4}(?![a-f0-9])|[a-f0-9:]*::[a-f0-9:]*)"
        + "|(?i:-----BEGIN |\\b(?:Bearer|Basic)\\s+\\S+|\\b(?:[a-z0-9]+[_-])*(?:password|passwd|pwd|secret|token|api[_ -]?key|client[_ -]?secret|пароль|токен|секрет)\\s*[:=]\\s*\\S+)"
        + "|(?i:\\b(?:drop|alter|create)\\s+(?:table|database)\\b|\\b(?:insert\\s+into|delete\\s+from)\\b)"
        + "|\\b(?:gh[pousr]_[A-Za-z0-9]{10,}|sk-[A-Za-z0-9]{10,}|AKIA[A-Z0-9]{16}|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)\\b");

    public static ArchitecturePassportInput parse(JsonNode body, boolean create) {
        if (body == null || !body.isObject()) throw ArchitectureMutationException.badRequest();
        String versionField = create ? "expectedProjectVersion" : "expectedVersion";
        body.fieldNames().forEachRemaining(field -> {
            if (!field.equals(versionField) && !FIELDS.contains(field)) throw ArchitectureMutationException.badRequest();
        });
        long version = version(body.get(versionField), versionField);
        if (!create && body.size() == 1) throw ArchitectureMutationException.badRequest();
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String field : FIELDS) {
            JsonNode value = body.get(field);
            if (value == null) {
                if (create && List.of("criticality", "stage", "solutionType").contains(field)) throw ArchitectureMutationException.invalid(field);
                continue;
            }
            Object parsed = switch (field) {
                case "criticality" -> code(value, field, CRITICALITY);
                case "stage" -> code(value, field, STAGE);
                case "solutionType" -> code(value, field, SOLUTION);
                case "architect" -> text(value, field, 255, false);
                case "solutionSummary" -> text(value, field, 2000, true);
                case "availabilityRequirement", "recoveryRequirement" -> text(value, field, 1000, true);
                case "lastReviewDate" -> date(value, field);
                case "dataClasses" -> codes(value, field, DATA);
                case "environments" -> codes(value, field, ENVIRONMENTS);
                default -> throw ArchitectureMutationException.badRequest();
            };
            changes.put(field, parsed);
        }
        return new ArchitecturePassportInput(version, Collections.unmodifiableMap(changes));
    }
    static long version(JsonNode value, String field) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
            || value.longValue() < 0 || value.longValue() == Long.MAX_VALUE) throw ArchitectureMutationException.invalid(field);
        return value.longValue();
    }
    static String code(JsonNode value, String field, Set<String> allowed) {
        if (!value.isTextual() || !allowed.contains(value.textValue())) throw ArchitectureMutationException.invalid(field);
        return value.textValue();
    }
    static Set<String> codes(JsonNode value, String field, Set<String> allowed) {
        if (!value.isArray() || value.size() > allowed.size()) throw ArchitectureMutationException.invalid(field);
        Set<String> result = new TreeSet<>();
        for (JsonNode item : value) if (!result.add(code(item, field, allowed))) throw ArchitectureMutationException.invalid(field);
        return Collections.unmodifiableSet(result);
    }
    static String text(JsonNode value, String field, int maxLength, boolean multiline) {
        if (value.isNull()) return null;
        if (!value.isTextual()) throw ArchitectureMutationException.invalid(field);
        String raw = value.textValue();
        for (int offset = 0; offset < raw.length();) {
            int cp = raw.codePointAt(offset); offset += Character.charCount(cp);
            if (Character.getType(cp) == Character.FORMAT || Character.getType(cp) == Character.SURROGATE
                || Character.isISOControl(cp) && !(multiline && (cp == '\n' || cp == '\r')))
                throw ArchitectureMutationException.invalid(field);
        }
        String normalized = Normalizer.normalize(raw.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC).strip();
        if (normalized.isBlank() || normalized.length() > maxLength || UNSAFE.matcher(normalized).find())
            throw ArchitectureMutationException.invalid(field);
        return normalized;
    }
    static LocalDate date(JsonNode value, String field) {
        if (value.isNull()) return null;
        if (!value.isTextual() || !value.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw ArchitectureMutationException.invalid(field);
        try { return LocalDate.parse(value.textValue()); }
        catch (DateTimeParseException invalid) { throw ArchitectureMutationException.invalid(field); }
    }
}
