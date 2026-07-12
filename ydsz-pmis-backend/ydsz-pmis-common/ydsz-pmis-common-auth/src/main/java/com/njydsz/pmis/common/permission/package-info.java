/**
 * 权限码层。
 *
 * <p>定义全平台统一的权限码（{@code permission_code}）常量与校验器。
 * 权限码采用"模块:资源:动作"三级命名（如 {@code project:contract:create}），
 * 与 {@code @AuthApiPermission} 注解配合实现接口级权限控制。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.permission.PermissionCodes}        - 权限码常量（按模块分组）</li>
 *   <li>{@link com.njydsz.pmis.common.permission.PermissionCodeValidator} - 权限码格式校验器（启动时自检）</li>
 * </ul>
 *
 * <h3>权限码命名规范</h3>
 * <ul>
 *   <li>模块名：小写，使用项目代号（如 {@code project} / {@code user} / {@code workflow}）</li>
 *   <li>资源名：小写、复数（如 {@code contracts} / {@code users}）</li>
 *   <li>动作：{@code create} / {@code update} / {@code delete} / {@code query} / {@code export} / {@code approve}</li>
 *   <li>通配：{@code *} 表示所有动作</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.permission;
