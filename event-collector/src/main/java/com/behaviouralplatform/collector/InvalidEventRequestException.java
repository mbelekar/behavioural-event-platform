package com.behaviouralplatform.collector;

import java.util.List;

class InvalidEventRequestException extends RuntimeException {

    private final List<String> fields;

    InvalidEventRequestException(List<String> fields) {
        super("Missing or invalid fields: " + fields);
        this.fields = fields;
    }

    List<String> fields() {
        return fields;
    }
}
