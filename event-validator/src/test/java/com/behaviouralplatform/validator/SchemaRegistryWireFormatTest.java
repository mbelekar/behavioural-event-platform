package com.behaviouralplatform.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SchemaRegistryWireFormatTest {

    @Test
    void prefixesMagicByteAndBigEndianSchemaId() {
        byte[] json = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        byte[] framed = SchemaRegistryWireFormat.frame(258, json);

        assertThat(framed[0]).isEqualTo((byte) 0);
        assertThat(ByteBuffer.wrap(framed, 1, 4).getInt()).isEqualTo(258);
        assertThat(Arrays.copyOfRange(framed, 5, framed.length)).isEqualTo(json);
    }
}
