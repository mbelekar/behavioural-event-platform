package com.behaviouralplatform.validator;

import java.util.List;

/** Design.md section 8, plus the id of the schema a valid event conformed to (needed for the wire format). */
record ValidationResult(boolean valid, List<ValidationError> errors, Integer schemaId) {

    static ValidationResult passed(int schemaId) {
        return new ValidationResult(true, List.of(), schemaId);
    }

    static ValidationResult failed(List<ValidationError> errors) {
        return new ValidationResult(false, List.copyOf(errors), null);
    }
}
