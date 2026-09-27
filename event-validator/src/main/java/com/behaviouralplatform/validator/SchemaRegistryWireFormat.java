package com.behaviouralplatform.validator;

import java.nio.ByteBuffer;

/** Confluent wire format: magic byte 0, 4-byte big-endian schema id, then the serialized value (ADR 0007). */
final class SchemaRegistryWireFormat {

    private static final byte MAGIC_BYTE = 0;

    private SchemaRegistryWireFormat() {
    }

    static byte[] frame(int schemaId, byte[] json) {
        return ByteBuffer.allocate(5 + json.length).put(MAGIC_BYTE).putInt(schemaId).put(json).array();
    }
}
