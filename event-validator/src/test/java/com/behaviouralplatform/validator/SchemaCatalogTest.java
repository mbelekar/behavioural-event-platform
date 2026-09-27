package com.behaviouralplatform.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.behaviouralplatform.schemas.SchemaRegistryContainers;
import com.behaviouralplatform.schemas.SharedSchemaRegistry;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.junit.jupiter.api.Test;

class SchemaCatalogTest {

    @Test
    void findsRegisteredSchemaWithItsId() throws Exception {
        SchemaRegistryClient client = SharedSchemaRegistry.client();
        int expectedId = client.getSchemaMetadata("product_viewed", 2).getId();

        SchemaLookup lookup = new SchemaCatalog(client).find("product_viewed", 2);

        assertThat(lookup).isInstanceOfSatisfying(SchemaLookup.Found.class, found ->
                assertThat(found.schemaId()).isEqualTo(expectedId));
    }

    @Test
    void unknownSubjectIsUnknownEventType() {
        assertThat(new SchemaCatalog(SharedSchemaRegistry.client()).find("wishlist_added", 1))
                .isInstanceOf(SchemaLookup.UnknownEventType.class);
    }

    @Test
    void unknownVersionIsUnknownSchemaVersion() {
        assertThat(new SchemaCatalog(SharedSchemaRegistry.client()).find("product_viewed", 9))
                .isInstanceOf(SchemaLookup.UnknownSchemaVersion.class);
    }

    @Test
    void envelopeSubjectIsNotAnEventType() {
        assertThat(new SchemaCatalog(SharedSchemaRegistry.client()).find("behavioural_envelope", 1))
                .isInstanceOf(SchemaLookup.UnknownEventType.class);
    }

    @Test
    void unreachableRegistryIsUnavailableNotUnknown() {
        SchemaCatalog catalog = new SchemaCatalog(SchemaRegistryContainers.newClient("http://localhost:1"));

        assertThatThrownBy(() -> catalog.find("product_viewed", 1))
                .isInstanceOf(SchemaRegistryUnavailableException.class);
    }

    @Test
    void cachesFoundSchemas() throws Exception {
        SchemaRegistryClient client = spy(SharedSchemaRegistry.client());
        SchemaCatalog catalog = new SchemaCatalog(client);

        catalog.find("page_viewed", 1);
        catalog.find("page_viewed", 1);

        verify(client, times(1)).getSchemaMetadata("page_viewed", 1);
    }
}
