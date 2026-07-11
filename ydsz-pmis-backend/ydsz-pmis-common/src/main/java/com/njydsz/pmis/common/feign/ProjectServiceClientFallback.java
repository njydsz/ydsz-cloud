package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 项目执行模块 Feign 降级工厂
 *
 * <p>project 服务不可用时返回空数据 Map，避免 Agent 工具级联失败。
 * Agent 工具在收到空数据时应安全降级（返回零值统计 / 空列表）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-5)
 */
@Slf4j
@Component
public class ProjectServiceClientFallback implements FallbackFactory<ProjectServiceClient> {

    @Override
    public ProjectServiceClient create(Throwable cause) {
        log.warn("[Feign] project 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new ProjectServiceClient() {
            @Override
            public Result<Map<String, Object>> timeEntryAbnormalStat(String initiationId, String month) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<Map<String, Object>> riskPage(int page, int size, String initiationId, String riskLevel) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<Map<String, Object>> evmDashboard(String initiationId) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
