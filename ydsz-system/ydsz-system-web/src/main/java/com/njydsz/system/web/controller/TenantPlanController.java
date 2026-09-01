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
import com.njydsz.system.domain.dto.TenantPlanDTO;
import com.njydsz.system.domain.dto.TenantPlanMenuDTO;
import com.njydsz.system.domain.query.TenantPlanPageQuery;
import com.njydsz.system.domain.vo.TenantPlanMenuVO;
import com.njydsz.system.domain.vo.TenantPlanVO;
import com.njydsz.system.server.service.TenantPlanMenuService;
import com.njydsz.system.server.service.TenantPlanService;

/**
 * 租户套餐管理 Controller
 *
 * <p>提供 SaaS 套餐的 CRUD、分页查询、菜单配置等管理能力。 套餐定义租户的功能 / 容量 / 价格，是 SaaS 多租户定价模型的核心。
 *
 * <p><b>接口路径：</b>{@code /api/v1/tenant-plan}
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see TenantPlanService 套餐业务逻辑
 */
@Tag(name = "租户套餐管理", description = "套餐 CRUD / 菜单配置")
@Slf4j
@RestController
@RequestMapping("/api/v1/tenant-plan")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:tenant:plan:list")
public class TenantPlanController {

  private final TenantPlanService planService;
  private final TenantPlanMenuService planMenuService;

  /**
   * 分页查询套餐列表
   *
   * @param query 分页查询条件（pageNum / pageSize / planName / status）
   * @return 分页结果
   */
  @Operation(summary = "分页查询套餐列表")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<TenantPlanVO>>> page(TenantPlanPageQuery query) {
    // pageSize 服务端硬上限截断，防止深度分页 OOM
    query.setPageSize(Math.min(query.getPageSize(), MAX_PAGE_SIZE));
    return YdszResponse.success(planService.page(query));
  }

  /**
   * 查询全部启用套餐
   *
   * <p>供租户注册页「选择套餐」下拉使用。
   *
   * @return 套餐列表
   */
  @Operation(summary = "查询全部启用套餐")
  @GetMapping("/list")
  public YdszResponse<List<TenantPlanVO>> listAll() {
    return YdszResponse.success(planService.listAll());
  }

  /**
   * 按 ID 查询套餐详情
   *
   * @param id 套餐 ID
   * @return 套餐详情
   */
  @Operation(summary = "按 ID 查询套餐")
  @GetMapping("/{id}")
  public YdszResponse<TenantPlanVO> getById(@PathVariable String id) {
    return YdszResponse.success(planService.getById(id));
  }

  /**
   * 创建套餐
   *
   * @param dto 套餐 DTO（命令入参）
   * @param userId 当前用户 ID
   * @return 新创建的套餐 ID
   */
  @Audit(
      module = "租户套餐管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建套餐: ' + #dto.planCode")
  @Operation(summary = "创建套餐")
  @RateLimit(resource = "system.tenant.plan.save", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:tenant-plan:save:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:tenant:plan:add")
  @PostMapping
  public YdszResponse<String> save(
      @Valid @RequestBody TenantPlanDTO dto,
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId) {
    return YdszResponse.success(planService.save(dto));
  }

  /**
   * 更新套餐
   *
   * @param dto 套餐 DTO（命令入参，必须包含 ID）
   * @param userId 当前用户 ID
   * @return 是否成功
   */
  @Audit(
      module = "租户套餐管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新套餐: ' + #dto.planCode")
  @Operation(summary = "更新套餐")
  @RateLimit(resource = "system.tenantplan.update", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:tenant-plan:update:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:tenant:plan:edit")
  @PutMapping
  public YdszResponse<Boolean> update(
      @Valid @RequestBody TenantPlanDTO dto,
      @RequestHeader(value = AuthHeaderConstants.X_USER_ID, required = false) String userId) {
    return YdszResponse.success(planService.updateById(dto));
  }

  /**
   * 删除套餐
   *
   * <p>删除前校验是否有关联租户，若存在关联租户则禁止删除。
   *
   * @param id 套餐 ID
   * @return 是否成功
   */
  @Audit(
      module = "租户套餐管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除套餐: ' + #id")
  @Operation(summary = "删除套餐")
  @RateLimit(resource = "system.tenant.plan.remove", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:tenant-plan:remove:' + T(com.njydsz.common.auth.context."
          + "AuthContextUtils).getUserId() + ':' + #id",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:tenant:plan:delete")
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(planService.removeById(id));
  }

  /**
   * 查询套餐关联的菜单列表
   *
   * @param planId 套餐 ID
   * @return 套餐-菜单关联列表
   */
  @Operation(summary = "查询套餐关联菜单")
  @GetMapping("/{planId}/menus")
  public YdszResponse<List<TenantPlanMenuVO>> listMenus(@PathVariable String planId) {
    return YdszResponse.success(planMenuService.listByPlanId(planId));
  }

  /**
   * 为套餐批量配置菜单权限
   *
   * <p>先删除该套餐的所有旧关联，再批量插入新的关联记录。
   *
   * @param dto 套餐-菜单关联 DTO
   * @return 是否成功
   */
  @Audit(
      module = "租户套餐管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'配置套餐菜单: ' + #dto.planId")
  @Operation(summary = "配置套餐菜单")
  @AuthApiPermission(apiCodes = "sys:tenant:plan:edit")
  @PostMapping("/menus")
  public YdszResponse<Void> updateMenus(@Valid @RequestBody TenantPlanMenuDTO dto) {
    planMenuService.updatePlanMenus(dto);
    return YdszResponse.success(null);
  }

  /** 分页安全上限 */
  private static final int MAX_PAGE_SIZE = 500;
}
