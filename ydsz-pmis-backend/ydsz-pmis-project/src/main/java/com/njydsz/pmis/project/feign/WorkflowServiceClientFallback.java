package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WorkflowServiceClient 降级工厂
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class WorkflowServiceClientFallback implements FallbackFactory<WorkflowServiceClient> {

    @Override
    public WorkflowServiceClient create(Throwable cause) {
        log.warn("[Feign] workflow 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new WorkflowServiceClient() {
            @Override
            public R<String> startProcess(Map<String, Object> body) {
                return R.failed(503, "工作流服务暂不可用");
            }

            @Override
            public R<Map<String, Object>> getByBusiness(String businessType, String businessId) {
                return R.ok(null);
            }

            @Override
            public R<Void> terminate(String processInstanceId, String reason) {
                return R.ok();
            }
        };
    }
}
