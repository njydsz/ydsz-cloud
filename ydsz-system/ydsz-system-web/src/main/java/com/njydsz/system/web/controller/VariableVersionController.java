package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.vo.EntityVersionVO;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.service.VariableService;

/**
 * 变量版本 Controller
 *
 * <p>提供变量变更历史查询和回滚能力。变量版本是变量变更审计与回滚能力的数据基础： 每次变量发生变更（save / updateById / removeById）时，{@link
 * com.njydsz.system.server.service.impl.VariableServiceImpl} 会自动创建一条版本快照。
 *
 * <p><b>接口路径：</b>{@code /api/v1/variable/version}
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>回滚接口启用 {@link Idempotent} 防重复提交
 *   <li>回滚接口启用 {@link RateLimit} 接口级限流（10 QPS，低频危险操作）
 *   <li>回滚接口启用 {@link Audit} 审计日志
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.server.service.EntityVersionService 统一实体版本业务逻辑
 * @see com.njydsz.system.infra.entity.EntityVersion 实体版本
 */
@Tag(name = "变量版本", description = "变量变更历史查询 + 一键回滚")
@RestController
@RequestMapping("/api/v1/variable/version")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:variable:version:list")
public class VariableVersionController {

  private final EntityVersionService entityVersionService;

  private final VariableService variableService;

  /**
   * 按变量键查询版本历史
   *
   * <p>返回该变量键下所有版本快照，按生效时间倒序（最新版本在前）。
   *
   * @param resourceKey 变量键（如 {@code "finance.current_fiscal_year"}）
   * @return 版本历史列表（可能为空）
   */
  @Operation(summary = "按变量键查询版本历史")
  @GetMapping("/{resourceKey}")
  public YdszResponse<List<EntityVersionVO>> listByResourceKey(@PathVariable String resourceKey) {
    return YdszResponse.success(
        entityVersionService.listByResourceTypeAndKey(
            EntityVersionService.RESOURCE_TYPE_VARIABLE, resourceKey));
  }

  /**
   * 执行变量版本回滚
   *
   * <p>将指定变量键回滚到目标版本，执行链路：
   *
   * <ol>
   *   <li>校验目标版本是否存在
   *   <li>查询当前变量作为回滚前快照（用于审计）
   *   <li>从目标快照更新变量
   *   <li>创建新版本记录（标记回滚来源）
   * </ol>
   *
   * <p><b>幂等保护：</b>同一 resourceKey + targetVersion 组合 30 秒内不可重复提交。
   *
   * <p><b>限流：</b>全局 10 QPS（低频危险操作）。
   *
   * @param resourceKey 变量键
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID（来自请求头 X-User-Id）
   * @return 新创建的回滚版本 ID
   */
  @Audit(
      module = "变量管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'回滚变量: ' + #resourceKey + ' → ' + #targetVersion")
  @Operation(summary = "回滚变量到指定版本", description = "将变量回滚到历史版本，操作不可撤销")
  @RateLimit(resource = "system.variable.rollback", threshold = 10)
  @Idempotent(
      key =
          "'ydsz:system:VariableVersionController:rollback:' + #resourceKey + ':' + #targetVersion",
      ttlSeconds = 30)
  @PostMapping("/{resourceKey}/rollback")
  public YdszResponse<String> rollback(
      @Parameter(description = "变量键") @PathVariable @NotBlank String resourceKey,
      @Parameter(description = "目标版本号") @RequestParam @NotBlank String targetVersion,
      @Parameter(description = "操作人 ID")
          @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false)
          String operatorId) {
    return YdszResponse.success(
        variableService.rollbackTo(resourceKey, targetVersion, operatorId));
  }
}
