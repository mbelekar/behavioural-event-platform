package com.behaviouralplatform.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaFilesTest {

    static final Path SCHEMAS = Path.of(System.getProperty("schemas.dir"));

    @Test
    void loadsEnvelopeFirstThenSubjectsAlphabeticallyWithVersionsAscending() throws Exception {
        List<SchemaFile> files = SchemaFiles.load(SCHEMAS);

        assertThat(files)
                .extracting(SchemaFile::subject, SchemaFile::version)
                .containsExactly(
                        tuple("behavioural_envelope", 1),
                        tuple("button_clicked", 1),
                        tuple("checkout_started", 1),
                        tuple("page_viewed", 1),
                        tuple("product_viewed", 1),
                        tuple("product_viewed", 2),
                        tuple("purchase_completed", 1),
                        tuple("search_performed", 1));
    }

    @Test
    void derivesEnvelopeReferenceFromRefName() throws Exception {
        SchemaFile productViewed = SchemaFiles.load(SCHEMAS).stream()
                .filter(f -> f.subject().equals("product_viewed") && f.version() == 1)
                .findFirst()
                .orElseThrow();

        assertThat(productViewed.references())
                .containsExactly(new SchemaReference("behavioural_envelope/v1.json", "behavioural_envelope", 1));
    }

    @Test
    void envelopeHasNoReferences() throws Exception {
        assertThat(SchemaFiles.load(SCHEMAS).getFirst().references()).isEmpty();
    }
}
