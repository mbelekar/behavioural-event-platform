package com.behaviouralplatform.validator;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Jackson 2 mapper: the Schema Registry libraries validate Jackson 2 trees, so the validator handles events with Jackson 2. */
final class Json {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}
}
