package com.behaviouralplatform.collector;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import com.behaviouralplatform.contracts.Topics;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final RawEventPublisher publisher;

    EventController(RawEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/v1/events")
    ResponseEntity<IngestionResponse> ingest(
            @RequestBody BehaviouralEvent request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {
        List<String> missing = EventRequestChecks.missingFields(request);
        if (!missing.isEmpty()) {
            throw new InvalidEventRequestException(missing);
        }
        BehaviouralEvent event = PlatformMetadata.apply(request, correlationId, Instant.now());
        publisher.publish(event);
        return ResponseEntity.accepted().body(new IngestionResponse(event.eventId(), "accepted"));
    }

    @ExceptionHandler(InvalidEventRequestException.class)
    ProblemDetail invalidRequest(InvalidEventRequestException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("fields", e.fields());
        return problem;
    }

    @ExceptionHandler(RequestTooLargeException.class)
    ProblemDetail requestTooLarge() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE, "Request body exceeds the maximum event size of 64 KB");
    }

    @ExceptionHandler(EventTooLargeException.class)
    ProblemDetail eventTooLarge(EventTooLargeException e) {
        log.warn(
                "Rejected event {}: too large for Kafka ({})",
                e.eventId(),
                e.getCause().getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE, "Event exceeds the maximum size accepted by the platform");
    }

    @ExceptionHandler(PublishFailedException.class)
    ProblemDetail publishFailed(PublishFailedException e) {
        log.warn("Could not publish event {} to {}", e.eventId(), Topics.RAW, e);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Event could not be accepted, retry later");
    }
}
