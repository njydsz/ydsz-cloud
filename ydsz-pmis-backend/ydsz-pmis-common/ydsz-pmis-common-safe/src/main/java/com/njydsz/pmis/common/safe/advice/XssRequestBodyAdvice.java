package com.njydsz.pmis.common.safe.advice;

import com.njydsz.pmis.common.safe.config.SafeXssProperties;
import com.njydsz.pmis.common.safe.core.JsonBodyXssCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * XSS 请求体拦截器
 * 
 * 在 JSON 反序列化前，对请求体中的字符串值进行 XSS 清理。
 * 适用于非 FastJson 转换器场景，作为补充防护层。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Component
@ConditionalOnProperty(prefix = "remi.safe.xss", name = "enabled", havingValue = "true", matchIfMissing = true)
public class XssRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private static final Logger log = LoggerFactory.getLogger(XssRequestBodyAdvice.class);

    private final JsonBodyXssCleaner xssCleaner;

    private final SafeXssProperties xssProperties;

    public XssRequestBodyAdvice(JsonBodyXssCleaner xssCleaner, SafeXssProperties xssProperties) {
        this.xssCleaner = xssCleaner;
        this.xssProperties = xssProperties;
    }

    @Override
    public boolean supports(@NonNull MethodParameter methodParameter, @NonNull Type targetType,
            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // 仅在 Filter 模式下生效，避免与 Converter 模式双重清洗
        return xssProperties.getMode() == SafeXssProperties.Mode.FILTER;
    }

    @Override
    @NonNull
    public HttpInputMessage beforeBodyRead(@NonNull HttpInputMessage inputMessage,
            @NonNull MethodParameter parameter, @NonNull Type targetType,
            @NonNull Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        // 只对 JSON 请求进行清理
        if (!isJsonContentType(inputMessage.getHeaders())) {
            return inputMessage;
        }

        byte[] bodyBytes = inputMessage.getBody().readAllBytes();
        if (bodyBytes == null || bodyBytes.length == 0) {
            return inputMessage;
        }

        String originalJson = new String(bodyBytes, StandardCharsets.UTF_8);
        String cleanedJson = xssCleaner.clean(originalJson);

        if (!cleanedJson.equals(originalJson)) {
            log.debug("[XssRequestBodyAdvice] JSON Body XSS 过滤完成, URI: {}", 
                    parameter.getMethod() != null ? parameter.getMethod().getName() : "unknown");
        }

        byte[] cleanedBytes = cleanedJson.getBytes(StandardCharsets.UTF_8);
        return new XssCleanedInputMessage(cleanedBytes, inputMessage.getHeaders());
    }

    /**
     * 判断 Content-Type 是否为 JSON
     */
    private boolean isJsonContentType(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        return contentType != null && contentType.includes(MediaType.APPLICATION_JSON);
    }

    /**
     * 包装清理后的 HTTP 输入消息
     */
    private static class XssCleanedInputMessage implements HttpInputMessage {

        private final byte[] body;
        private final HttpHeaders headers;

        XssCleanedInputMessage(byte[] body, HttpHeaders headers) {
            this.body = body;
            this.headers = headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
