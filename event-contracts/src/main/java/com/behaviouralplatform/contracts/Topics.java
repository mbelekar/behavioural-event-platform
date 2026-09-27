package com.behaviouralplatform.contracts;

/** Kafka topics shared between the services. They are created by infrastructure (docker-compose.yml locally). */
public final class Topics {

    public static final String RAW = "behavioural.raw";
    public static final String VALID = "behavioural.valid";
    public static final String INVALID = "behavioural.invalid";
    public static final String DLQ = "validation.dlq";

    private Topics() {}
}
