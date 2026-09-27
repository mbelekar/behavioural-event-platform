package com.behaviouralplatform.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.databind.node.ObjectNode;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RawEventPublisher publisher;

    @Test
    void acceptsValidEvent() throws Exception {
        mvc.perform(post("/v1/events").contentType(APPLICATION_JSON).content(TestEvents.VALID_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value("01K5R4F8W8J5Z8XJH0N6F4P2C1"))
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void publishesEventWithPlatformMetadata() throws Exception {
        mvc.perform(post("/v1/events")
                        .contentType(APPLICATION_JSON)
                        .header("X-Correlation-Id", "req-789")
                        .content(TestEvents.VALID_JSON))
                .andExpect(status().isAccepted());

        ArgumentCaptor<BehaviouralEvent> published = ArgumentCaptor.forClass(BehaviouralEvent.class);
        verify(publisher).publish(published.capture());
        assertThat(published.getValue().correlationId()).isEqualTo("req-789");
        assertThat(published.getValue().receivedAt()).isNotNull();
    }

    @Test
    void rejectsMissingRequiredFieldsWithoutPublishing() throws Exception {
        ObjectNode node = TestEvents.validNode();
        node.remove("eventType");
        node.remove("userId");
        node.remove("sessionId");

        mvc.perform(post("/v1/events").contentType(APPLICATION_JSON).content(node.toString()))
                .andExpect(problem(400))
                .andExpect(jsonPath("$.fields", containsInAnyOrder("eventType", "userId|sessionId")));
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsUnknownTopLevelField() throws Exception {
        ObjectNode node = TestEvents.validNode();
        node.put("eventTyp", "typo");

        mvc.perform(post("/v1/events").contentType(APPLICATION_JSON).content(node.toString()))
                .andExpect(problem(400));
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/v1/events").contentType(APPLICATION_JSON).content("{not json"))
                .andExpect(problem(400));
    }

    @Test
    void rejectsUnparseableTimestamp() throws Exception {
        ObjectNode node = TestEvents.validNode();
        node.put("occurredAt", "yesterday");

        mvc.perform(post("/v1/events").contentType(APPLICATION_JSON).content(node.toString()))
                .andExpect(problem(400));
    }

    @Test
    void returnsServiceUnavailableWhenPublishFails() throws Exception {
        doThrow(new PublishFailedException("01K5R4F8W8J5Z8XJH0N6F4P2C1", new RuntimeException("broker down")))
                .when(publisher)
                .publish(any());

        mvc.perform(post("/v1/events").contentType(APPLICATION_JSON).content(TestEvents.VALID_JSON))
                .andExpect(problem(503));
    }

    @Test
    void returnsPayloadTooLargeWhenKafkaRejectsTheEventSize() throws Exception {
        doThrow(new EventTooLargeException("01K5R4F8W8J5Z8XJH0N6F4P2C1", new RuntimeException("record too large")))
                .when(publisher)
                .publish(any());

        mvc.perform(post("/v1/events").contentType(APPLICATION_JSON).content(TestEvents.VALID_JSON))
                .andExpect(problem(413));
    }

    /** Every error response uses the same RFC 9457 ProblemDetail shape. */
    private static ResultMatcher problem(int status) {
        return result -> {
            status().is(status).match(result);
            content().contentType(APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(status).match(result);
        };
    }
}
