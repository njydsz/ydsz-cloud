package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * P2-1: AgentClient Fallback 工厂
 *
 * <p>Agent 服务不可用时，返回"降级"占位结果，保证工作流主流程不受影响。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentClientFallbackFactory implements FallbackFactory<AgentClient> {

    @Override
    public AgentClient create(Throwable cause) {
        log.warn("[AgentClient] Feign fallback triggered: {}", cause.getMessage());
        return body -> {
            // 返回一个标准的"服务降级"占位响应
            return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
        };
    }
}
