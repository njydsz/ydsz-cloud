package com.njydsz.workflow.api.fallback;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.workflow.api.client.WorkflowServiceClient;

import lombok.extern.slf4j.Slf4j;

/**
 * WorkflowServiceClient 降级工厂
 *
 * <p>所有方法在服务不可用时统一返回
 * {@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码，
 * 禁止返回 success(null) 或 success()。
 *
 * @author ydsz-team
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
                return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
            }

            @Override
            public BaseResponse<Map<String, Object>> getByBusiness(String businessType, String businessId) {
                return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
            }

            @Override
            public BaseResponse<Void> terminate(String processInstanceId, String reason) {
                return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
            }
        };
    }
}
