package com.behaviouralplatform.collector;

/** The request body exceeds the collector's event size cap (ADR 0010). Retrying cannot succeed. */
class RequestTooLargeException extends RuntimeException {

    RequestTooLargeException() {
        super("Request body exceeds " + RequestSizeLimit.MAX_BYTES + " bytes");
    }
}
