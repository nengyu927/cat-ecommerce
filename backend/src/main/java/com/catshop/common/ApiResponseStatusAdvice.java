package com.catshop.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Match the numeric code to the actual HTTP status while keeping the legacy envelope fields. */
@RestControllerAdvice
public class ApiResponseStatusAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter parameter, MediaType type,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> envelope && response instanceof ServletServerHttpResponse servlet) {
            envelope.setCode(servlet.getServletResponse().getStatus());
        }
        return body;
    }
}
