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
import org.jspecify.annotations.NonNull;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * XSS 璇锋眰浣撴嫤鎴櫒
 * 
 * 鍦?JSON 鍙嶅簭鍒楀寲鍓嶏紝瀵硅姹備綋涓殑瀛楃涓插€艰繘琛?XSS 娓呯悊銆?
 * 閫傜敤浜庨潪 FastJson 杞崲鍣ㄥ満鏅紝浣滀负琛ュ厖闃叉姢灞傘€?
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
        // 浠呭湪 Filter 妯″紡涓嬬敓鏁堬紝閬垮厤涓?Converter 妯″紡鍙岄噸娓呮礂
        return xssProperties.getMode() == SafeXssProperties.Mode.FILTER;
    }

    @Override
    @NonNull
    public HttpInputMessage beforeBodyRead(@NonNull HttpInputMessage inputMessage,
            @NonNull MethodParameter parameter, @NonNull Type targetType,
            @NonNull Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        // 鍙 JSON 璇锋眰杩涜娓呯悊
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
            log.debug("[XssRequestBodyAdvice] JSON Body XSS 杩囨护瀹屾垚, URI: {}", 
                    parameter.getMethod() != null ? parameter.getMethod().getName() : "unknown");
        }

        byte[] cleanedBytes = cleanedJson.getBytes(StandardCharsets.UTF_8);
        return new XssCleanedInputMessage(cleanedBytes, inputMessage.getHeaders());
    }

    /**
     * 鍒ゆ柇 Content-Type 鏄惁涓?JSON
     */
    private boolean isJsonContentType(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        return contentType != null && contentType.includes(MediaType.APPLICATION_JSON);
    }

    /**
     * 鍖呰娓呯悊鍚庣殑 HTTP 杈撳叆娑堟伅
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
