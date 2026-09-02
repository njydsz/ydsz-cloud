package com.njydsz.workflow.web.controller.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;

/**
 * 内部 API Controller（服务间 Feign 调用）
 *
 * <p>为 <b>跨服务 Feign 调用</b> 提供统一 HTTP 入口，是 {@code ydsz-gateway} 之外的服务间点对点调用通道。
 * 这些端点<b>仅用于服务间通信</b>，不应直接对外暴露。
 *
 * <p><b>接口路径：</b>{@code /api/internal/**}
 *
 * <p><b>安全要求：</b>
 *
 * <ul>
 *   <li>Gateway 应限制 {@code /api/internal/**} 仅允许<b>内部服务 IP</b>调用（白名单），对公网不可访问
 *   <li>敏感参数通过 <b>POST body</b> 传输，<b>严禁</b>出现在 URL 中，避免被网关日志记录
 *   <li>所有接口启用 {@link RateLimit} 接口级限流（100 QPS），防止被恶意刷接口
 *   <li>启动/终止接口启用 {@link Idempotent} 幂等保护（5 秒），避免重试风暴
 * </ul>
 *
 * <p><b>响应契约：</b>所有端点统一返回 {@link YdszResponse} 包装，与 {@code ydsz-workflow-api} 模块中
 * {@code WorkflowServiceClient} 的 Feign 声明严格对齐，避免跨服务反序列化失败。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.api.client.WorkflowServiceClient Feign Client 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "workflow:internal:api")
public class InternalWorkflowApiController {

  private final WorkflowFacade workflowFacade;

  /**
   * 启动流程实例
   *
   * <p>对应自研引擎 {@code ydsz_flow_*}：POST /api/internal/engine/instance/start
   *
   * @param dto 启动参数（含 flowCode / businessType / businessId / variables / initiatorId）
   * @return 流程实例 ID
   */
  @RateLimit(resource = "workflow.internalapi.startProcess", threshold = 100)
  @Idempotent(
      key = "'ydsz:workflow:internal-api:start-process:' + #dto.flowCode + ':' + #dto.businessKey",
      ttlSeconds = 5)
  @PostMapping("/engine/instance/start")
  public YdszResponse<String> startProcess(@RequestBody FlowStartProcessDTO dto) {
    return YdszResponse.success(workflowFacade.startProcess(dto));
  }

  /**
   * 通过业务单据反查流程状态
   *
   * <p>对应自研引擎：GET /api/internal/engine/instance/byBusiness
   *
   * @param businessType 业务类型
   * @param businessId 业务单据 ID
   * @return 流程实例视图对象
   */
  @RateLimit(resource = "workflow.internalapi.getByBusiness", threshold = 100)
  @Idempotent(
      key = "'ydsz:workflow:internal-api:get-by-business:' + #businessType + ':' + #businessId",
      ttlSeconds = 5)
  @GetMapping("/engine/instance/byBusiness")
  public YdszResponse<FlowInstanceVO> getByBusiness(
      @RequestParam("businessType") String businessType,
      @RequestParam("businessId") String businessId) {
    return YdszResponse.success(
        (FlowInstanceVO) workflowFacade.getByBusiness(businessType, businessId));
  }

  /**
   * 终止流程实例
   *
   * <p>对应自研引擎：POST /api/internal/engine/instance/{id}/terminate
   *
   * @param processInstanceId 流程实例 ID
   * @param reason 终止原因（可空）
   * @return 终止结果
   */
  @RateLimit(resource = "workflow.internalapi.terminate", threshold = 50)
  @Idempotent(
      key =
          "'ydsz:workflow:internal-api:terminate:' + #processInstanceId + ':' + #reason.hashCode()",
      ttlSeconds = 5)
  @PostMapping("/engine/instance/{id}/terminate")
  public YdszResponse<Void> terminate(
      @PathVariable("id") String processInstanceId,
      @RequestParam(value = "reason", required = false) String reason) {
    workflowFacade.terminateProcess(processInstanceId, reason);
    return YdszResponse.success(null);
  }
}
