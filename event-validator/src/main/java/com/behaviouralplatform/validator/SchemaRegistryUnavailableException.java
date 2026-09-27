package com.behaviouralplatform.validator;

/** Schema Registry could not answer. Retried; never treated as an invalid event. */
class SchemaRegistryUnavailableException extends RuntimeException {

    SchemaRegistryUnavailableException(String lookup, Throwable cause) {
        super("Schema Registry unavailable while looking up " + lookup, cause);
    }
}
