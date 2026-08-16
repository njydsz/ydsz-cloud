package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 角色实体
 *
 * <p>对应数据库表 {@code ydsz_role}，是 RBAC（基于角色的访问控制）模型的核心实体。
 * 一个角色可被多个用户共享，一个角色可拥有多个菜单/权限点，通过中间表 {@link UserRole}
 * 和 {@link RolePermission} 维护多对多关系。
 *
 * <p><b>RBAC 链路示意：</b>
 * <pre>
 *   UserAccount ─(N)── UserRole ──(N)── Role ──(N)── RolePermission ──(N)── Menu
 *       用户              中间表          角色              中间表             权限点
 * </pre>
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code roleCode}：角色编码（业务侧引用，全局唯一，建议格式 {@code ROLE_XXX}）</li>
 *   <li>{@code roleName}：角色名称（前端展示）</li>
 *   <li>{@code dataScope}：数据权限范围（ALL/DEPT_AND_CHILD/DEPT/SELF/CUSTOM），控制可见数据行</li>
 *   <li>{@code builtIn}：是否内置角色（{@code true} 时禁止删除/编辑编码，保护系统角色）</li>
 *   <li>{@code tenantId}：租户 ID（多租户隔离，{@code "0"} = 平台级角色）</li>
 * </ul>
 *
 * <p><b>内置角色（{@code builtIn=true}）保护：</b>
 * <ul>
 *   <li>SUPER_ADMIN（超级管理员，平台级）</li>
 *   <li>TENANT_ADMIN（租户管理员）</li>
 *   <li>AUDITOR（审计只读）</li>
 *   <li>GUEST（访客）</li>
 * </ul>
 * 内置角色不可删除、不可修改 {@code roleCode}，可调整权限分配。
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_role_code}（{@code role_code}），
 * 普通索引 {@code idx_tenant_id}（{@code tenant_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see UserRole 用户-角色中间表
 * @see RolePermission 角色-权限中间表
 * @see com.njydsz.userinfo.web.controller.RoleController 角色 Controller
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_role")
public class Role extends MpBaseEntity<String> {

    /** 角色编码（业务侧引用，全局唯一，建议格式 {@code ROLE_XXX}） */
    private String roleCode;

    /** 角色名称（前端展示） */
    private String roleName;

    /** 角色描述（说明该角色的业务定位与适用场景） */
    private String description;

    /** 同级排序序号（升序） */
    private Integer sortOrder;

    /**
     * 启用状态（{@code "ENABLED"} / {@code "DISABLED"}）
     *
     * <p>禁用后，拥有该角色的用户暂时无法访问系统，但用户-角色关联不删除。
     */
    private String status;

    /**
     * 是否内置角色。
     *
     * <p>内置角色（{@code true}）保护机制：① 禁止删除；② 禁止修改 {@code roleCode}；
     * ③ 可调整权限分配和数据范围。用于保护系统核心角色（SUPER_ADMIN 等）。
     */
    private Boolean builtIn;

    /**
     * 数据权限范围。
     *
     * <p>取值（对应枚举 {@code DataScopeEnum}）：
     * <ul>
     *   <li>{@code ALL}：全部数据（无限制）</li>
     *   <li>{@code DEPT_AND_CHILD}：本部门及子部门数据</li>
     *   <li>{@code DEPT}：仅本部门数据</li>
     *   <li>{@code SELF}：仅本人数据</li>
     *   <li>{@code CUSTOM}：自定义部门（需配合 {@code role_custom_dept} 中间表）</li>
     * </ul>
     */
    private String dataScope;

    /**
     * 租户 ID。
     *
     * <p>{@code "0"} 表示平台级角色（所有租户可见）；其它值为租户级角色（仅对应租户可见）。
     */
    private String tenantId;
}
