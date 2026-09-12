package com.njydsz.workflow.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.workflow.api.client.WorkflowServiceClient;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;

/**
 * WorkflowServiceClient 降级工厂
 *
 * <p>所有方法在服务不可用时统一返回 {@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码， 禁止返回 success(null)
 * 或 success()。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class WorkflowServiceClientFallback implements FallbackFactory<WorkflowServiceClient> {

  /** {@inheritDoc} */
  @Override
  /** 降级工厂实现：根据异常构造降级客户端实例。
   *
   * @param cause 触发降级的异常
   * @return 降级客户端实例
   */
  public WorkflowServiceClient create(Throwable cause) {
    log.warn("[Feign] workflow 服务降级: {}", cause == null ? "?" : cause.getMessage());
    return new WorkflowServiceClient() {
      /** {@inheritDoc} */
      @Override
  /** 降级启动流程：返回失败响应，不阻塞调用方。
   *
   * @param dto 启动参数（被忽略）
   * @return 降级响应（SERVICE_UNAVAILABLE）
   */
  
      public YdszResponse<String> startProcess(FlowStartProcessDTO dto) {
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
      }

      /** {@inheritDoc} */
      @Override
  /** 降级查询：返回空实例，不抛出异常。
   *
   * @param businessType 业务类型（被忽略）
   * @param businessId 业务 ID（被忽略）
   * @return 空实例 VO
   */
  
      public YdszResponse<FlowInstanceVO> getByBusiness(String businessType, String businessId) {
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
      }

      /** {@inheritDoc} */
      @Override
  /** 降级终止流程：返回失败响应。
   *
   * @param processInstanceId 实例 ID（被忽略）
   * @param reason 终止原因（被忽略）
   * @return 降级响应
   */
  
      public YdszResponse<Void> terminate(String processInstanceId, String reason) {
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "工作流服务不可用");
      }
    };
  }
}
