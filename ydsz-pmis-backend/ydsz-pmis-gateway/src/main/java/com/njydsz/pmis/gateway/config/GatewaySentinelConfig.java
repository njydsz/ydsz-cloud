package com.njydsz.pmis.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.Result;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.charset.StandardCharsets;

/**
 * 网关 Sentinel 配置
 *
 * <p>自定义网关层限流/熔断响应，统一返回 R 格式 JSON。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class GatewaySentinelConfig {

    /**
     * 初始化网关限流响应处理器
     */
    @PostConstruct
    public void init() {
        BlockRequestHandler handler = (exchange, ex) -> {
            Result<?> body = Result.failed(429, "网关限流: " + ex.getClass().getSimpleName());
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8)
                    .bodyValue(JSON.toJSONString(body));
        };
        GatewayCallbackManager.setBlockHandler(handler);
    }
}
