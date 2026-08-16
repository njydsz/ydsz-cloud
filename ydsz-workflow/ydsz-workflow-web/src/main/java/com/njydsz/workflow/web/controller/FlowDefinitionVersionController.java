package com.njydsz.workflow.web.controller.definition;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import com.njydsz.workflow.server.service.FlowDefinitionService;

/**
 * 流程定义版本生命周期管理 Controller
 *
 * <p>提供流程定义的版本切换 / 启停 / 版本历史查询 / 版本差异对比 / 一键回滚等 REST 接口， 是版本治理与运维回滚的核心入口。
 *
 * <p><b>业务背景：</b>对标 Activiti / Flowable 的版本管理能力。同一 flowCode 下可存在
 * 多个版本，通过激活版本机制确保运行时实例引用稳定；版本切换支持灰度回退与在途实例自动迁移。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>版本切换</b>：{@code POST /definition/{code}/switchVersion}（切换激活版本）
 *   <li><b>启停控制</b>：{@code POST /definition/{id}/enable}（启用） / {@code POST
 *       /definition/{id}/disable}（停用）
 *   <li><b>版本历史</b>：{@code GET /definition/{id}/versions}（版本列表） / {@code GET
 *       /definition/{id}/diff}（版本差异对比）
 *   <li><b>回滚</b>：{@code POST /definition/rollback}（一键回滚到上一版本，自动迁移在途实例）
 * </ul>
 *
 * <p><b>权限模型：</b>写接口通过 {@link AuthApiPermission} 校验 {@link
 * PermissionCodes#WORKFLOW_DEFINITION_PUBLISH} 权限码； 回滚接口通过 {@link
 * PermissionCodes#WORKFLOW_DEFINITION_DESIGN} 权限码控制。
 *
 * <p><b>限流与幂等：</b>启停接口通过 {@link RateLimit} 限流（50 QPS）， 版本切换与启停通过 {@link Idempotent} 保证「同一请求 5s
 * 内只执行一次」。
 *
 * <p><b>拆分说明：</b>本类从原 {@code FlowDefinitionController} 拆分而来，仅保留版本生命周期管理类接口。 部署 / 发布 / 查询类接口见 {@link
 * FlowDefinitionController}； 设计 / 导入导出 / 模拟类接口见 {@link FlowDefinitionDesignController}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowDefinitionService 流程定义服务
 * @see FlowDefinitionController 部署 / 发布 / 查询接口
 * @see FlowDefinitionDesignController 设计 / 导入导出 / 模拟接口
 */
@Slf4j
@RestController
@Tag(name = "workflow-definition-version", description = "工作流流程定义版本生命周期管理接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDefinitionVersionController {

  /** 流程定义服务 */
  private final FlowDefinitionService definitionService;

  /**
   * P2-27: 切换流程定义的激活版本
   *
   * @param code 流程编码
   * @param definitionId 目标流程定义 ID
   * @param tenantId 租户 ID（可选）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:switchVersion:lock", ttlSeconds = 5)
  @PostMapping("/definition/{code}/switchVersion")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'switchVersion'")
  @Operation(summary = "切换流程定义的激活版本")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
  public BaseResponse<Void> switchVersion(
      @PathVariable String code,
      @RequestParam String definitionId,
      @RequestParam(required = false) String tenantId) {
    definitionService.switchActiveVersion(code, definitionId, tenantId);
    return BaseResponse.success();
  }

  /**
   * P2-28: 启用流程定义
   *
   * @param id 流程定义 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:enable:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdefinition.enable", threshold = 50)
  @PostMapping("/definition/{id}/enable")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.ENABLE,
      content = "'enable'")
  @Operation(summary = "启用流程定义")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
  public BaseResponse<Void> enable(@PathVariable String id) {
    definitionService.enable(id);
    return BaseResponse.success();
  }

  /**
   * P2-28: 停用流程定义
   *
   * @param id 流程定义 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:disable:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdefinition.disable", threshold = 50)
  @PostMapping("/definition/{id}/disable")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.DISABLE,
      content = "'disable'")
  @Operation(summary = "停用流程定义")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
  public BaseResponse<Void> disable(@PathVariable String id) {
    definitionService.disable(id);
    return BaseResponse.success();
  }

  /**
   * 列出流程定义的所有历史版本
   *
   * @param id 流程定义 ID
   * @return 统一响应结果，包含版本列表
   */
  @GetMapping("/definition/{id}/versions")
  @Operation(summary = "列出流程定义的所有历史版本")
  public BaseResponse<List<Map<String, Object>>> listVersions(@PathVariable String id) {
    return BaseResponse.success(definitionService.listVersions(id));
  }

  /**
   * 版本差异对比
   *
   * @param id 流程定义 ID
   * @param v1 版本号 1
   * @param v2 版本号 2
   * @return 统一响应结果，包含 nodeChanges 和 skipChanges
   */
  @GetMapping("/definition/{id}/diff")
  @Operation(summary = "流程定义版本差异对比")
  public BaseResponse<Map<String, Object>> diffVersions(
      @PathVariable String id, @RequestParam Integer v1, @RequestParam Integer v2) {
    return BaseResponse.success(definitionService.diffVersions(id, v1, v2));
  }

  /**
   * P0-2: 流程定义一键回滚
   *
   * <p>将指定 flowCode 的激活版本切换回上一个已发布版本， 并自动迁移在途实例。HIGH 风险时阻止回滚。
   *
   * @param flowCode 流程编码
   * @return 统一响应结果，包含回滚报告
   */
  @PostMapping("/definition/rollback")
  @Operation(summary = "一键回滚流程定义到上一版本")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public BaseResponse<Map<String, Object>> rollbackDefinition(@RequestParam String flowCode) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(definitionService.rollbackDefinition(flowCode, tenantId));
  }
}
