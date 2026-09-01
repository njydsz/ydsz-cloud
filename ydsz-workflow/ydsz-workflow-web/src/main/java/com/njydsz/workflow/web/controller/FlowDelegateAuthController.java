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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.domain.dto.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;

/**
 * 委托授权控制器（P1-6 拆分自 FlowTaskController，路由不变）。
 *
 * <p>提供长期授权委派的创建、撤回、启停与双视角查询（我设置的/代理给我的）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@RestController
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
public class FlowDelegateAuthController {

  /** P1-4: 长期授权委派服务 */
  private final FlowDelegateAuthService delegateAuthService;

  /**
   * 创建长期授权委派。
   *
   * @param dto 委派授权创建参数
   * @return 授权记录 ID
   */
  @PostMapping("/delegateAuth/create")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'createDelegateAuth'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  @Operation(summary = "创建长期授权委派")
  public YdszResponse<String> createDelegateAuth(@Valid @RequestBody FlowDelegateAuthPostDTO dto) {
    var auth = delegateAuthService.postDtoToVO(dto);
    if (auth.getOwnerUserId() == null) {
      auth.setOwnerUserId(AuthContextUtils.getUserId());
    }
    String id = delegateAuthService.create(auth);
    return YdszResponse.success(id);
  }

  /**
   * P1-4: 撤回授权。
   *
   * @param id 授权记录 ID
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:delegate:revoke", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdelegate.revokeDelegateAuth", threshold = 50)
  @PostMapping("/delegateAuth/{id}/revoke")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'revokeDelegateAuth'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  @Operation(summary = "撤回授权")
  public YdszResponse<Void> revokeDelegateAuth(@PathVariable String id) {
    String ownerId = AuthContextUtils.getUserId();
    delegateAuthService.revoke(id, ownerId);
    return YdszResponse.success();
  }

  /**
   * P1-4: 启用/停用授权。
   *
   * @param id 授权记录 ID
   * @param status 目标状态
   * @return 空响应
   */
  @Idempotent(
      key = "ydsz:workflow:FlowDelegateAuthController:updateDelegateAuthStatus:lock",
      ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdelegate.updateDelegateAuthStatus", threshold = 50)
  @PostMapping("/delegateAuth/{id}/status")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'updateDelegateAuthStatus'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  @Operation(summary = "启用停用授权")
  public YdszResponse<Void> updateDelegateAuthStatus(
      @PathVariable String id, @RequestParam String status) {
    String operatorId = AuthContextUtils.getUserId();
    delegateAuthService.updateStatus(id, status, operatorId);
    return YdszResponse.success();
  }

  /**
   * P1-4: 查"我设置的"授权列表。
   *
   * @param status 状态筛选（可选）
   * @return 授权列表
   */
  @GetMapping("/delegateAuth/mine")
  @Operation(summary = "查询我设置的授权列表")
  public YdszResponse<List<FlowDelegateAuthVO>> listMyDelegateAuths(
      @RequestParam(required = false) String status) {
    String ownerId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(
        delegateAuthService.listMine(ownerId, tenantId, status));
  }

  /**
   * P1-4: 查"代理给我的"授权列表。
   *
   * @param status 状态筛选（可选）
   * @return 授权列表
   */
  @GetMapping("/delegateAuth/asDelegate")
  @Operation(summary = "查询代理给我的授权列表")
  public YdszResponse<List<FlowDelegateAuthVO>> listAsDelegate(
      @RequestParam(required = false) String status) {
    String delegateUserId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(
        delegateAuthService.listAsDelegate(delegateUserId, tenantId, status));
  }
}
