package com.njydsz.workflow.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.domain.query.FlowCcQuery;
import com.njydsz.workflow.domain.vo.FlowCcVO;
import com.njydsz.workflow.server.service.FlowCcService;

/**
 * 抄送中心控制器（P1-6 拆分自 FlowTaskController，路由不变）。
 *
 * <p>提供抄送分页查询、未读数、单条/全部已读能力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@RestController
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
public class FlowCcController {

  /** P0-3: 抄送服务 */
  private final FlowCcService ccService;

  /**
   * P0-3: 抄送中心 - 分页查询。
   *
   * @param query 查询条件
   * @return 抄送分页结果
   */
  @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
  @RateLimit(resource = "workflow.FlowCc.pageCc", threshold = 50)
  @Idempotent(key = "ydsz:workflow:cc:page", ttlSeconds = 5)
  @PostMapping("/cc/page")
  @Audit(
      module = "流程抄送",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'pageCc'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CC_VIEW)
  @Operation(summary = "抄送中心分页查询")
  public YdszResponse<List<FlowCcVO>> pageCc(@Valid @RequestBody FlowCcQuery query) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    String userId = AuthContextUtils.getUserId();
    int pageNo = query.getPageNum();
    int pageSize = query.getPageSize();
    return ccService.listCcByUser(
        userId, query.getReadStatus(), query.getFlowCode(), tenantId, pageNo, pageSize);
  }

  /**
   * P0-3: 抄送未读数（前端导航栏徽标）。
   *
   * @return 未读抄送条数
   */
  @GetMapping("/cc/unreadCount")
  @Operation(summary = "抄送未读数")
  public YdszResponse<Long> ccUnreadCount() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    String userId = AuthContextUtils.getUserId();
    return YdszResponse.success(ccService.countUnread(userId, tenantId));
  }

  /**
   * P0-3: 抄送标记已读。
   *
   * @param id 抄送记录 ID
   * @return 操作结果
   */
  @Idempotent(key = "ydsz:workflow:cc:markRead", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowCc.ccMarkRead", threshold = 50)
  @PostMapping("/cc/{id}/read")
  @Audit(
      module = "流程抄送",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'ccMarkRead'")
  @Operation(summary = "抄送标记已读")
  public YdszResponse<Boolean> ccMarkRead(@PathVariable String id) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    String userId = AuthContextUtils.getUserId();
    ccService.markRead(tenantId, userId, id);
    return YdszResponse.success(Boolean.TRUE);
  }

  /**
   * P0-3: 抄送全部标记已读。
   *
   * @return 已标记已读的记录数
   */
  @Idempotent(key = "ydsz:workflow:cc:markAllRead", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowCc.ccMarkAllRead", threshold = 50)
  @PostMapping("/cc/readAll")
  @Audit(
      module = "流程抄送",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'ccMarkAllRead'")
  @Operation(summary = "抄送全部标记已读")
  public YdszResponse<Integer> ccMarkAllRead() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    String userId = AuthContextUtils.getUserId();
    return YdszResponse.success(ccService.markAllRead(tenantId, userId));
  }
}
