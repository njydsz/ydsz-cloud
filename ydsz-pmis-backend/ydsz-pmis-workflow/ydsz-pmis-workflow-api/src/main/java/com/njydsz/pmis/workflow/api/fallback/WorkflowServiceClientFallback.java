package com.njydsz.pmis.workflow.api.fallback;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.workflow.api.client.WorkflowServiceClient;

import lombok.extern.slf4j.Slf4j;

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
            public BaseResponse<String> startProcess(Map<String, Object> body) {
                return BaseResponse.failed(BaseResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<Map<String, Object>> getByBusiness(String businessType, String businessId) {
                return BaseResponse.ok(null);
            }

            @Override
            public BaseResponse<Void> terminate(String processInstanceId, String reason) {
                return BaseResponse.ok();
            }
        };
    }
}
