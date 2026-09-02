package com.njydsz.userinfo.web.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.server.service.MenuService;

/**
 * 菜单/权限 Controller
 *
 * <p>提供菜单的完整管理能力（CRUD）、菜单树查询、当前用户菜单查询。 菜单（{@code ydsz_rbac_menu}）是 RBAC 模型中最细粒度的「权限点」，既可以表示前端路由节点，
 * 也可以表示后端接口权限码（如 {@code system:user:create}）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/Menu}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>菜单分页/列表查询
 *   <li>菜单 CRUD（含 {@code parentId} 树形关联）
 *   <li>菜单树查询（{@code /tree}）：返回整棵菜单树，前端管理系统渲染
 *   <li>当前用户菜单查询（{@code /current}）：根据当前用户角色，返回可见菜单（树形）
 *   <li>菜单启用/禁用（{@code /enable} / {@code /disable}）
 * </ul>
 *
 * <p><b>菜单 vs 权限码：</b>
 *
 * <ul>
 *   <li>本 Controller 管理的「菜单」含前端路由信息和权限码 {@code permCode}
 *   <li>角色-权限分配通过 {@link RoleController#assignPermissions} 绑定菜单到角色
 *   <li>后端接口鉴权通过 {@code @AuthApiPermission(apiCodes=...)} 引用同一份 {@code permCode}
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交
 *   <li>写接口启用 {@link RateLimit} 接口级限流
 *   <li>写接口启用 {@link Audit} 审计日志
 *   <li>菜单删除会校验是否被角色引用（避免悬挂引用）
 *   <li>菜单变更会触发权限缓存失效（通过 {@code PermissionCacheInvalidator}）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.userinfo.server.service.MenuService 菜单业务逻辑
 * @see com.njydsz.userinfo.domain.vo.MenuVO 菜单VO
 * @see com.njydsz.userinfo.web.controller.RoleController 角色 Controller（关联分配）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
@Tag(name = "菜单管理", description = "菜单/权限 CRUD、树形结构查询")
public class MenuController {

  private final MenuService service;

  /**
   * 查询全部菜单列表（扁平结构）
   *
   * <p>返回全量菜单 VO（不构建树形结构），按 {@code sortOrder} 升序排列。
   *
   * <p>适用于需要扁平数据 + 客户端自行构建树的场景。
   *
   * @return 全部未删除菜单列表
   */
  @GetMapping("/list")
  @Operation(summary = "查询全部菜单列表")
  public YdszResponse<List<MenuVO>> list() {
    return YdszResponse.success(service.list());
  }

  /**
   * 查询菜单树形结构
   *
   * <p>返回整棵菜单树，前端管理系统渲染菜单管理页面。
   *
   * <p>由 Service 层在内存中构建树（递归遍历全量列表）。
   *
   * @return 菜单树形结构列表（每个节点含 children）
   */
  @GetMapping("/tree")
  @Operation(summary = "查询菜单树形结构")
  public YdszResponse<List<MenuTreeVO>> tree() {
    return YdszResponse.success(service.tree());
  }

  /**
   * 根据 ID 查询菜单
   *
   * @param id 菜单 ID
   * @return 菜单详情；不存在或已删除时返回 null
   */
  @GetMapping("/{id}")
  @Operation(summary = "根据 ID 查询菜单")
  public YdszResponse<MenuVO> getById(@PathVariable String id) {
    return YdszResponse.success(service.getById(id));
  }

  /**
   * 创建菜单
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>业务流程：menuCode / permissionCode 唯一性校验 → 写入 DB → 触发权限缓存失效。
   *
   * <p>创建根菜单时 {@code parentId} 应传 {@code "0"}（约定值）。
   *
   * @param dto 菜单创建 DTO（menuName / menuCode / menuType / path / component / permissionCode）
   * @return 新创建的菜单 ID
   */
  @Audit(
      module = "菜单管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建菜单: ' + #dto.menuName")
  @Idempotent(key = "ydsz:userinfo:MenuController:create:lock", ttlSeconds = 5)
  @RateLimit(resource = "userinfo.Menu.create", threshold = 50)
  @PostMapping
  @Operation(summary = "创建菜单")
  public YdszResponse<String> create(@Valid @RequestBody MenuDTO dto) {
    return YdszResponse.success(service.create(dto));
  }

  /**
   * 更新菜单
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>业务流程：使用 {@code BeanUpdateUtil.copyNonNull} 动态复制非 null 字段。
   *
   * <p>修改 {@code permissionCode} 会同步影响后端接口鉴权，<b>需谨慎</b>。
   *
   * @param dto 菜单更新 DTO（必须包含 ID）
   * @return 是否成功
   */
  @Audit(
      module = "菜单管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新菜单: ' + #dto.id")
  @Idempotent(key = "ydsz:userinfo:MenuController:update:lock", ttlSeconds = 5)
  @RateLimit(resource = "userinfo.Menu.update", threshold = 50)
  @PutMapping
  @Operation(summary = "更新菜单")
  public YdszResponse<Boolean> update(@Valid @RequestBody MenuDTO dto) {
    return YdszResponse.success(service.update(dto));
  }

  /**
   * 按 ID 删除菜单
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>删除前置校验：
   *
   * <ul>
   *   <li>有<b>子菜单</b>的菜单<b>禁止删除</b>
   *   <li>有<b>角色引用</b>的菜单<b>禁止删除</b>（避免悬挂引用）
   * </ul>
   *
   * <p>如需删除带子菜单的菜单，<b>必须先</b>递归删除/迁移子菜单。
   *
   * @param id 菜单 ID
   * @return 是否成功
   */
  @Audit(
      module = "菜单管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除菜单: ' + #id")
  @RateLimit(resource = "userinfo.Menu.remove", threshold = 50)
  @Idempotent(key = "ydsz:userinfo:MenuController:remove:lock", ttlSeconds = 5)
  @DeleteMapping("/{id}")
  @Operation(summary = "删除菜单")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(service.removeById(id));
  }
}
