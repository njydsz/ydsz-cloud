package com.njydsz.pmis.common.web;

import com.njydsz.pmis.common.annotation.DeprecatedApi;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * API 版本废弃响应拦截器（P2-5：API 版本废弃策略）
 *
 * <p>对标注 {@link DeprecatedApi} 的方法自动添加 HTTP 响应头：
 * <ul>
 *   <li>{@code Deprecation: true}</li>
 *   <li>{@code Sunset: date}（若配置了 sunset）</li>
 *   <li>{@code Link: <alternative>; rel="deprecation"}</li>
 *   <li>{@code Warning: 299 - "Deprecated API: reason"}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@ControllerAdvice
public class DeprecatedApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(DeprecatedApi.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        DeprecatedApi annotation = returnType.getMethodAnnotation(DeprecatedApi.class);
        if (annotation == null) return body;

        // 标准 Deprecation 头
        response.getHeaders().set("Deprecation", "true");

        // Sunset 头（计划移除日期）
        if (!annotation.sunset().isEmpty()) {
            response.getHeaders().set("Sunset", annotation.sunset());
        }

        // Link 头（替代 API）
        if (!annotation.alternative().isEmpty()) {
            response.getHeaders().set("Link",
                    "<" + annotation.alternative() + ">; rel=\"deprecation\"");
        }

        // Warning 头（废弃原因）
        if (!annotation.reason().isEmpty()) {
            response.getHeaders().set("Warning",
                    "299 - \"Deprecated API: " + annotation.reason().replace("\"", "\\\"") + "\"");
        }

        return body;
    }
}