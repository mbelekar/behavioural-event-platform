package com.behaviouralplatform.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.everit.json.schema.ValidationException;

/** Turns JSON Schema violations into structured errors with dotted field paths. */
final class SchemaViolations {

    /** "required key [productId] not found", "extraneous key [debug] is not permitted" */
    private static final Pattern BRACKETED = Pattern.compile("\\[(.+?)]");

    private SchemaViolations() {}

    static List<ValidationError> toErrors(ValidationException root) {
        List<ValidationError> errors = new ArrayList<>();
        collect(root, errors);
        return errors;
    }

    private static void collect(ValidationException e, List<ValidationError> errors) {
        if (!e.getCausingExceptions().isEmpty()) {
            e.getCausingExceptions().forEach(cause -> collect(cause, errors));
            return;
        }
        String path = toPath(e.getPointerToViolation());
        String keyword = e.getKeyword() == null ? "" : e.getKeyword();
        String message = e.getErrorMessage().isBlank() ? "violates '" + keyword + "'" : e.getErrorMessage();
        errors.add(
                switch (keyword) {
                    case "required" ->
                        new ValidationError("REQUIRED_FIELD_MISSING", child(path, bracketed(message)), message);
                    case "additionalProperties" ->
                        new ValidationError("UNKNOWN_FIELD", child(path, bracketed(message)), message);
                    case "type" -> new ValidationError("INVALID_TYPE", path, message);
                    case "format" -> new ValidationError("INVALID_FORMAT", path, message);
                    default -> new ValidationError("SCHEMA_VIOLATION", path, message);
                });
    }

    /** "#/payload/productId" → "payload.productId"; "#" → "". */
    static String toPath(String pointer) {
        return pointer == null || pointer.equals("#")
                ? ""
                : pointer.substring(2).replace('/', '.');
    }

    private static String child(String parent, String name) {
        return parent.isEmpty() ? name : parent + "." + name;
    }

    private static String bracketed(String message) {
        Matcher m = BRACKETED.matcher(message);
        return m.find() ? m.group(1) : "";
    }
}
