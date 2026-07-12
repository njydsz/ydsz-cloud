package com.njydsz.pmis.workflow.api.fallback;
import com.njydsz.pmis.workflow.api.client.WorkflowServiceClient;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.core.response.BaseResponse;
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
            public BaseResponse<String> startProcess(Map<String, Object> body) {
                return BaseResponse.failed(StandardResultCode.SERVICE_UNAVAILABLE);
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
