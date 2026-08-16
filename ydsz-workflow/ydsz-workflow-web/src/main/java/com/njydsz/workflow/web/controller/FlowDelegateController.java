package com.njydsz.workflow.web.controller.delegate;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.dto.post.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.domain.dto.put.FlowDelegateAuthPutDTO;
import com.njydsz.workflow.domain.entity.FlowDelegateAuth;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;

/**
 * 长期授权委派 Controller（P1-4）
 *
 * <p>提供工作流「长期授权委派」HTTP API，对标钉钉/飞书审批"审批委托"模块。 与短期一次性转办（{@code
 * FlowTaskController.transfer}）不同，长期委派用于 员工<b>休假 / 出差 / 离职过渡期</b>的持续性代理。
 *
 * <p><b>业务示例：</b>用户 A 休假 7 天，希望 B 代理处理所有流程
 *
 * <pre>
 * {
 *   "ownerUserId": 1001,
 *   "ownerUserName": "张三",
 *   "delegateUserId": 1002,
 *   "delegateUserName": "李四",
 *   "scopeType": "ALL",
 *   "startTime": "2026-07-02T00:00:00",
 *   "endTime": "2026-07-09T23:59:59",
 *   "reason": "年假"
 * }
 * </pre>
 *
 * <p><b>委派范围（scopeType）：</b>
 *
 * <ul>
 *   <li><b>ALL</b>：所有流程（默认）
 *   <li><b>FLOW</b>：指定流程（需传 {@code flowCode}）
 *   <li><b>NODE</b>：指定流程 + 节点（需传 {@code flowCode} + {@code nodeCode}）
 *   <li><b>ROLE</b>：指定角色（需传 {@code roleCode}）
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/engine/delegateAuth/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>创建 / 撤回</b>：{@code POST /delegateAuth/create} / {@code POST .../revoke}
 *   <li><b>启停控制</b>：{@code POST /delegateAuth/{id}/status}
 *   <li><b>列表查询</b>：{@code GET /mine}（我设置的）/ {@code GET /asDelegate}（代理给我的）
 *   <li><b>日志查询</b>：{@code GET /log/delegate}（我代理处理了哪些）/ {@code GET /log/owner}（我的哪些被代理）
 * </ul>
 *
 * <p><b>权限模型：</b>写接口通过 {@link AuthApiPermission} 校验 {@link
 * PermissionCodes#WORKFLOW_DELEGATE_MANAGE} 权限码；读接口基于 SecurityContext 自动按 userId 过滤。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重（避免双击创建重复授权）
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>撤销操作要求 ownerUserId == 当前用户（防越权）
 *   <li>授权时间窗口由 Service 层校验（startTime &lt; endTime + 未过期）
 * </ul>
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传、权限校验、DTO 转换； 时间窗口校验、状态机、委派日志写入下沉到 {@link
 * FlowDelegateAuthService}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowDelegateAuthService 长期授权委派服务
 * @see FlowDelegateAuth 长期授权实体
 * @see FlowDelegateAuthPostDTO 创建授权 DTO
 * @see FlowDelegateAuthPutDTO 更新授权 DTO
 */
@Slf4j
@RestController
@Tag(name = "workflow-delegate", description = "工作流授权委派接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDelegateController {

  /** P1-4: 长期授权委派服务 */
  private final FlowDelegateAuthService delegateAuthService;

  /**
   * P1-4: 创建长期授权委派
   *
   * <p>业务示例：用户 A 休假 7 天，希望 B 代理处理所有流程。 提交时 body 形如：
   *
   * <pre>
   * {
   *   "ownerUserId": 1001,
   *   "ownerUserName": "张三",
   *   "delegateUserId": 1002,
   *   "delegateUserName": "李四",
   *   "scopeType": "ALL",
   *   "startTime": "2026-07-02T00:00:00",
   *   "endTime": "2026-07-09T23:59:59",
   *   "reason": "年假"
   * }
   * </pre>
   */
  @Idempotent(key = "ydsz:workflow:FlowDelegateController:createDelegateAuth:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdelegate.createDelegateAuth", threshold = 50)
  @PostMapping("/delegateAuth/create")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'createDelegateAuth'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  public BaseResponse<String> createDelegateAuth(@Valid @RequestBody FlowDelegateAuthPostDTO dto) {
    FlowDelegateAuth auth = WorkflowConverter.INSTANT.postDtoToEntity(dto);
    // 从 SecurityContext 兜底 ownerUserId（防止前端漏传）
    if (auth.getOwnerUserId() == null) {
      auth.setOwnerUserId(AuthContextUtils.getUserId());
    }
    String id = delegateAuthService.create(auth);
    return BaseResponse.success(id);
  }

  /**
   * P1-4: 撤回授权。
   *
   * @param id 授权记录 ID
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowDelegateController:revokeDelegateAuth:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdelegate.revokeDelegateAuth", threshold = 50)
  @PostMapping("/delegateAuth/{id}/revoke")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'revokeDelegateAuth'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  public BaseResponse<Void> revokeDelegateAuth(@PathVariable String id) {
    String ownerId = AuthContextUtils.getUserId();
    delegateAuthService.revoke(id, ownerId);
    return BaseResponse.success();
  }

  /**
   * P1-4: 启用/停用授权。
   *
   * @param id 授权记录 ID
   * @param status 目标状态
   * @return 空响应
   */
  @Idempotent(
      key = "ydsz:workflow:FlowDelegateController:updateDelegateAuthStatus:lock",
      ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdelegate.updateDelegateAuthStatus", threshold = 50)
  @PostMapping("/delegateAuth/{id}/status")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'updateDelegateAuthStatus'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  public BaseResponse<Void> updateDelegateAuthStatus(
      @PathVariable String id, @RequestParam String status) {
    String operatorId = AuthContextUtils.getUserId();
    delegateAuthService.updateStatus(id, status, operatorId);
    return BaseResponse.success();
  }

  /**
   * P1-4: 查"我设置的"授权列表。
   *
   * @param status 状态筛选（可选）
   * @return 授权列表
   */
  @GetMapping("/delegateAuth/mine")
  public BaseResponse<List<FlowDelegateAuthVO>> listMyDelegateAuths(
      @RequestParam(required = false) String status) {
    String ownerId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowDelegateAuthListToVO(
            delegateAuthService.listMine(ownerId, tenantId, status)));
  }

  /**
   * P1-4: 查"代理给我的"授权列表。
   *
   * @param status 状态筛选（可选）
   * @return 授权列表
   */
  @GetMapping("/delegateAuth/asDelegate")
  public BaseResponse<List<FlowDelegateAuthVO>> listAsDelegate(
      @RequestParam(required = false) String status) {
    String delegateUserId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowDelegateAuthListToVO(
            delegateAuthService.listAsDelegate(delegateUserId, tenantId, status)));
  }

  /**
   * P1-4: 查"我代理处理了哪些任务"。
   *
   * @param page 页码
   * @param size 每页大小
   * @return 委派处理日志分页
   */
  @GetMapping("/delegateAuth/log/delegate")
  public BaseResponse<?> myDelegateLog(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    String delegateUserId = AuthContextUtils.getUserId();
    return delegateAuthService.listDelegateLog(delegateUserId, page, size);
  }

  /**
   * P1-4: 查"我的哪些任务被代理了"。
   *
   * @param page 页码
   * @param size 每页大小
   * @return 被代理任务日志分页
   */
  @GetMapping("/delegateAuth/log/owner")
  public BaseResponse<?> myOwnerLog(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    String ownerUserId = AuthContextUtils.getUserId();
    return delegateAuthService.listOwnerLog(ownerUserId, page, size);
  }
}
