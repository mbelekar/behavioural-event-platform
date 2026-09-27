package com.behaviouralplatform.collector;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Rejects event bodies over 64 KB before they are parsed (ADR 0010). */
@ControllerAdvice
class RequestSizeLimit extends RequestBodyAdviceAdapter {

    static final int MAX_BYTES = 64 * 1024;

    @Override
    public boolean supports(
            MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == BehaviouralEvent.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage input,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        if (input.getHeaders().getContentLength() > MAX_BYTES) {
            throw new RequestTooLargeException();
        }
        byte[] body = input.getBody().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            throw new RequestTooLargeException();
        }
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return input.getHeaders();
            }
        };
    }
}
