package com.njydsz.common.feign.aspect;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.feign.config.FeignProperties;
import com.njydsz.common.util.http.RequestContextUtils;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.util.string.StringUtils;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Feign核心请求头透传拦截器
 * 
 * <p>仅透传4个通用核心请求头，保证链路可追溯、租户上下文透传：
 * 1. traceparent：W3C标准链路追踪头
 * 2. X-Tenant-Id：租户上下文标识
 * 3. X-Access-Token：用户访问令牌
 * 4. X-Request-Id：请求唯一标识，用于问题排查
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FeignRequestInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignRequestInterceptor.class);

    private final FeignProperties feignProperties;

    public FeignRequestInterceptor(FeignProperties feignProperties) {
        this.feignProperties = feignProperties;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        if (feignProperties == null
                || feignProperties.getPropagation() == null
                || !feignProperties.getPropagation().isEnabled()
                || feignProperties.getPropagation().getHeaders() == null
                || feignProperties.getPropagation().getHeaders().isEmpty()) {
            return;
        }

        HttpServletRequest request = RequestContextUtils.getRequest();
        Set<String> headersToPropagate = feignProperties.getPropagation().getHeaders();

        // 透传链路追踪头
        if (headersToPropagate.contains("traceparent") && !hasHeader(requestTemplate, "traceparent")) {
            requestTemplate.header("traceparent", TracerUtils.getCurrentTraceParent());
        }

        // 透传其他3个核心头
        propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Tenant-Id");
        propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Access-Token");

        // 处理请求ID透传，不存在时自动生成
        if (headersToPropagate.contains("X-Request-Id") && !hasHeader(requestTemplate, "X-Request-Id")) {
            String requestId = request != null ? request.getHeader("X-Request-Id") : null;
            if (StringUtils.isEmpty(requestId)) {
                requestId = TracerUtils.getTraceId();
                if (StringUtils.isEmpty(requestId)) {
                    requestId = TracerUtils.generateTraceId();
                }
            }
            requestTemplate.header("X-Request-Id", requestId);
        }
    }

    /**
     * 透传简单类型的请求头，从HttpServletRequest获取后写入
     */
    private void propagateSimpleHeader(RequestTemplate requestTemplate,
                                       HttpServletRequest request,
                                       Set<String> headersToPropagate,
                                       String headerName) {
        if (!headersToPropagate.contains(headerName) || hasHeader(requestTemplate, headerName)) {
            return;
        }
        String value = request != null ? request.getHeader(headerName) : null;
        if (StringUtils.isNotEmpty(value)) {
            requestTemplate.header(headerName, value);
        }
    }

    /**
     * 判断请求头是否已存在
     */
    private boolean hasHeader(RequestTemplate requestTemplate, String headerName) {
        return requestTemplate.headers() != null
                && requestTemplate.headers().get(headerName) != null
                && !requestTemplate.headers().get(headerName).isEmpty();
    }
}