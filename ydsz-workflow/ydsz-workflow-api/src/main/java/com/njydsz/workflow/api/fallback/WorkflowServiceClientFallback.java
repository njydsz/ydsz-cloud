package com.njydsz.workflow.api.fallback;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.workflow.api.client.WorkflowServiceClient;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * WorkflowServiceClient 降级工厂
 *
 * <p>所有方法在服务不可用时统一返回 {@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码， 禁止返回 success(null)
 * 或 success()。
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
      public BaseResponse<String> startProcess(FlowStartProcessDTO dto) {
        return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
      }

      @Override
      public BaseResponse<FlowInstanceVO> getByBusiness(String businessType, String businessId) {
        return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
      }

      @Override
      public BaseResponse<Void> terminate(String processInstanceId, String reason) {
        return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
      }
    };
  }
}
