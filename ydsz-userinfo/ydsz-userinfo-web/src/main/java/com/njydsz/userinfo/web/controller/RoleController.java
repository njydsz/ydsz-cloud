package com.njydsz.userinfo.web.controller;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
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
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.userinfo.domain.dto.AssignPermissionsDTO;
import com.njydsz.userinfo.domain.dto.RolePageQueryDTO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.service.RoleService;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.njydsz.userinfo.domain.dto.post.RolePostDTO;
import com.njydsz.userinfo.domain.dto.put.RolePutDTO;

/**
 * 角色 Controller
 *
 * <p>提供角色的完整管理能力（CRUD）、角色权限分配/撤销、角色-用户关联查询。
 * 角色是 RBAC（基于角色的访问控制）模型的核心实体，连接用户与权限：
 * <pre>
 *   UserAccount (N) ──── UserRole (中间表) ──── Role (1) ──── RoleMenu (中间表) ──── Menu (N)
 *   用户                         角色                           角色拥有的菜单权限
 * </pre>
 *
 * <p><b>接口路径：</b>{@code /api/v1/role}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>角色分页查询（按名称/编码/状态过滤）</li>
 *   <li>角色 CRUD（创建/更新/删除/详情）</li>
 *   <li>角色-权限分配（{@code /assign-permissions}）：批量绑定菜单权限</li>
 *   <li>角色-用户查询（{@code /users}）：查询某角色下的所有用户</li>
 *   <li>角色启用/禁用（{@code /enable} / {@code /disable}）</li>
 * </ul>
 *
 * <p><b>权限分配设计：</b>角色 → 菜单（{@code ydsz_menu}）通过 {@code ydsz_role_menu} 中间表多对多关联。
 * 分配权限实际是写中间表，{@code assignPermissions} 接口一次性清空旧关联并写入新关联（事务保证）。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交</li>
 *   <li>写接口启用 {@link RateLimit} 接口级限流</li>
 *   <li>写接口启用 {@link Audit} 审计日志</li>
 *   <li>角色删除会校验是否被用户引用（业务层拦截，避免悬挂引用）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.server.service.RoleService 角色业务逻辑
 * @see com.njydsz.userinfo.web.controller.MenuController 菜单 Controller（权限分配的目标对象）
 * @see com.njydsz.userinfo.web.controller.UserAccountController 用户 Controller（角色授予目标）
 */
@RestController
@RequestMapping("/api/v1/role")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色 CRUD、权限分配")
public class RoleController {

    private final RoleService service;

    /**
     * 分页查询角色列表
     *
     * <p>支持按 roleCode / roleName 模糊匹配 + status 精确匹配，结果按 {@code sortOrder} 升序排列。
     * <p>结果集启用 {@code @DataScope} 自动追加部门过滤。
     *
     * @param query 分页查询条件（pageNum / pageSize / roleCode / roleName / status）
     * @return 分页结果（含总记录数、当前页、每页大小、数据列表）
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询角色列表")
    public PageResponse<List<RoleVO>> page(@Valid RolePageQueryDTO query) {
        Page<RoleVO> page = service.page(query);
        return PageResponse.success(
                page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 查询全部角色列表（不翻页）
     *
     * <p>按 sortOrder 升序排列，适用于角色下拉框、单选按钮组等场景。
     *
     * @return 全部未删除角色列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询全部角色列表")
    public BaseResponse<List<RoleVO>> list() {
        return BaseResponse.success(service.list());
    }

    /**
     * 根据 ID 查询角色
     *
     * @param id 角色 ID
     * @return 角色详情；不存在或已删除时返回 null
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询角色")
    public BaseResponse<RoleVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    /**
     * 创建角色
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p>业务流程：roleCode 唯一性校验 → 写入 DB。
     * <p>{@code builtIn=true} 的内置角色由系统初始化时创建，<b>不允许</b>通过本接口创建。
     *
     * @param dto 角色创建 DTO（roleCode / roleName / description / dataScope 等）
     * @return 新创建的角色 ID
     */
    @Audit(module = "角色管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建角色: ' + #dto.roleName")
    @Idempotent(key = "ydsz:userinfo:RoleController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.role.create", threshold = 50)
    @PostMapping
    @Operation(summary = "创建角色")
    public BaseResponse<String> create(@Valid @RequestBody RolePostDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    /**
     * 更新角色
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p>业务流程：使用 {@code BeanUpdateUtil.copyNonNull} 动态复制非 null 字段，
     * <b>忽略 builtIn 字段</b>（不允许通过 API 变更内置角色标识）。
     *
     * @param dto 角色更新 DTO（必须包含 ID）
     * @return 是否成功
     */
    @Audit(module = "角色管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新角色: ' + #dto.id")
    @Idempotent(key = "ydsz:userinfo:RoleController:update:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.role.update", threshold = 50)
    @PutMapping
    @Operation(summary = "更新角色")
    public BaseResponse<Boolean> update(@Valid @RequestBody RolePutDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    /**
     * 按 ID 删除角色
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p>删除前置校验：
     * <ul>
     *   <li>内置角色（{@code builtIn=true}）<b>禁止删除</b></li>
     *   <li>仍有用户关联的角色<b>禁止删除</b>（避免悬挂引用）</li>
     * </ul>
     * <p>删除时同时清除角色-权限关联记录（中间表）。
     *
     * @param id 角色 ID
     * @return 是否成功
     */
    @Audit(module = "角色管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除角色: ' + #id")
    @RateLimit(resource = "userinfo.role.remove", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:RoleController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }

    /**
     * 分配角色权限
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p><b>覆盖式</b>分配：先清空旧的权限关联，再批量插入新关联（避免 N+1 循环）。
     * 业务方传入<b>完整</b>的权限 ID 列表，而非增量。
     *
     * @param roleId 角色 ID
     * @param dto    分配权限 DTO（permissionIds 列表）
     * @return 是否成功
     */
    @Audit(module = "角色管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'分配角色权限: ' + #roleId")
    @Idempotent(key = "ydsz:userinfo:RoleController:assignPermissions:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.role.assignPermissions", threshold = 50)
    @PostMapping("/{roleId}/permissions")
    @Operation(summary = "分配角色权限")
    public BaseResponse<Boolean> assignPermissions(
            @PathVariable String roleId,
            @Valid @RequestBody AssignPermissionsDTO dto) {
        return BaseResponse.success(service.assignPermissions(roleId, dto.getPermissionIds()));
    }

    /**
     * 查询角色的权限 ID 列表
     *
     * <p>返回该角色拥有的全部权限（菜单）ID 列表；常用于角色编辑页的「已选权限」回显。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "查询角色权限 ID 列表")
    public BaseResponse<List<String>> getRolePermissions(@PathVariable String roleId) {
        return BaseResponse.success(service.getRolePermissionIds(roleId));
    }
}
