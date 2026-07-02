package com.njydsz.pmis.common.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

/**
 * Sentinel 自动配置
 *
 * <p>为 MVC 模块提供统一的限流/熔断 BlockException 响应处理。
 * 网关模块(响应式)通过 sentinel-spring-cloud-gateway-adapter 自带处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(BlockExceptionHandler.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SentinelAutoConfiguration {

    /**
     * 统一 BlockException 响应处理
     *
     * <p>将 Sentinel 的 BlockException 转换为项目统一的 R 响应格式:
     * <ul>
     *   <li>FlowException → 429 RATE_LIMIT</li>
     *   <li>DegradeException → 503 SERVICE_UNAVAILABLE</li>
     *   <li>SystemBlockException → 503 SERVICE_UNAVAILABLE</li>
     * </ul>
     *
     * @return BlockExceptionHandler 处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public BlockExceptionHandler sentinelBlockExceptionHandler() {
        return (request, response, e) -> {
            Result<?> body;
            if (e instanceof FlowException) {
                response.setStatus(429);
                body = Result.failed(BizErrorCode.RATE_LIMIT, "请求频率超限，请稍后再试");
            } else if (e instanceof DegradeException) {
                response.setStatus(503);
                body = Result.failed(BizErrorCode.SERVICE_UNAVAILABLE, "服务降级保护中，请稍后再试");
            } else if (e instanceof SystemBlockException) {
                response.setStatus(503);
                body = Result.failed(BizErrorCode.SERVICE_UNAVAILABLE, "系统负载过高，已触发保护");
            } else {
                response.setStatus(429);
                body = Result.failed(BizErrorCode.RATE_LIMIT, "请求被限流: " + e.getClass().getSimpleName());
            }
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(JSON.toJSONString(body));
        };
    }
}
