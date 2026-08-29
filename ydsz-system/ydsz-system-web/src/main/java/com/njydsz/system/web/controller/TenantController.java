package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.dto.TenantDTO;
import com.njydsz.system.domain.query.TenantPageQuery;
import com.njydsz.system.domain.vo.TenantVO;
import com.njydsz.system.server.service.TenantService;

/**
 * 租户管理 Controller
 *
 * <p>提供 SaaS 多租户的 CRUD、分页查询等管理能力。 租户是系统多租户隔离的最高层，支持套餐绑定、配额管理、到期控制。
 *
 * <p><b>接口路径：</b>{@code /api/v1/tenant}
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交
 *   <li>写接口启用 {@link RateLimit} 接口级限流
 *   <li>写接口启用 {@link Audit} 审计日志
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TenantService 租户业务逻辑
 */
@Tag(name = "租户管理", description = "多租户 CRUD")
@Slf4j
@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:tenant:list")
public class TenantController {

  private final TenantService service;

  /**
   * 分页查询租户列表
   *
   * <p>支持按租户名称模糊搜索和状态精确过滤。
   *
   * @param query 分页查询条件（pageNum / pageSize / tenantName / status）
   * @return 分页结果
   */
  @Operation(summary = "分页查询租户列表")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<TenantVO>>> page(TenantPageQuery query) {
    // pageSize 服务端硬上限截断，防止深度分页 OOM
    query.setPageSize(Math.min(query.getPageSize(), MAX_PAGE_SIZE));
    return YdszResponse.success(service.page(query));
  }

  /**
   * 按 ID 查询租户详情
   *
   * @param id 租户 ID
   * @return 租户详情
   */
  @Operation(summary = "按 ID 查询租户")
  @GetMapping("/{id}")
  public YdszResponse<TenantVO> getById(@PathVariable String id) {
    return YdszResponse.success(service.getById(id));
  }

  /**
   * 创建租户
   *
   * <p>写入前校验租户编码全局唯一性。
   *
   * @param dto 租户 DTO
   * @param userId 当前用户 ID（来自网关透传）
   * @return 新创建的租户 ID
   */
  @Audit(
      module = "租户管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建租户: ' + #dto.tenantCode")
  @Operation(summary = "创建租户")
  @RateLimit(resource = "system.tenant.save", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:tenant:save:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:tenant:add")
  @PostMapping
  public YdszResponse<String> save(
      @Valid @RequestBody TenantDTO dto,
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId) {
    return YdszResponse.success(service.save(dto));
  }

  /**
   * 更新租户
   *
   * @param dto 租户 DTO（必须包含 ID）
   * @param userId 当前用户 ID（来自网关透传）
   * @return 是否成功
   */
  @Audit(
      module = "租户管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新租户: ' + #dto.tenantCode")
  @Operation(summary = "更新租户")
  @RateLimit(resource = "system.tenant.update", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:tenant:update:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:tenant:edit")
  @PutMapping
  public YdszResponse<Boolean> update(
      @Valid @RequestBody TenantDTO dto,
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId) {
    return YdszResponse.success(service.updateById(dto));
  }

  /**
   * 删除租户（逻辑删除）
   *
   * @param id 租户 ID
   * @return 是否成功
   */
  @Audit(
      module = "租户管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除租户: ' + #id")
  @Operation(summary = "删除租户")
  @RateLimit(resource = "system.tenant.remove", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:tenant:remove:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId() + ':' + #id",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:tenant:delete")
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(service.removeById(id));
  }

  /** 分页安全上限：防止 pageSize=999999 导致深度分页 OOM */
  private static final int MAX_PAGE_SIZE = 500;
}
