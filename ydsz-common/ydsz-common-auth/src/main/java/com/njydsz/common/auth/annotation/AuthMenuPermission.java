package com.njydsz.common.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 菜单/按钮权限校验注解。
 *
 * <p>用于标注在 Controller 或具体接口方法上，校验用户是否拥有指定的菜单或按钮权限。 支持菜单级别（MENU）和按钮级别（BUTTON）两种权限类型，支持通配符匹配。
 *
 * <p><b>校验链路：</b>
 *
 * <ol>
 *   <li>从请求头 X-Access-Token 获取 token
 *   <li>根据 token 从 Redis 获取用户信息（含 roleCode，支持多角色 CSV 格式）
 *   <li>根据 roleCode 加载角色关联的菜单/按钮权限集合（来自 Redis role-menu-key）
 *   <li>按注解配置的 mode（AND/OR）进行权限匹配
 *   <li>超级管理员（配置在 ignoreRoles）直接放行
 * </ol>
 *
 * <p><b>与 {@link AuthApiPermission} 的区别：</b>
 *
 * <ul>
 *   <li>{@link AuthApiPermission}：校验接口级别的访问权限，权限码通常粒度较细
 *   <li>{@link AuthMenuPermission}：校验菜单/按钮级别的操作权限，权限码通常与前端菜单按钮对应
 *   <li>实际项目中可根据业务需求选择使用或组合使用
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // 校验菜单权限（用户菜单树中是否包含该菜单）
 * &#64;AuthMenuPermission(permissionCodes = "sys:user:menu", type = PermissionType.MENU)
 * public MenuVO getUserMenu() { ... }
 *
 * // 校验按钮权限（用户是否有操作按钮的权限）
 * &#64;AuthMenuPermission(permissionCodes = "sys:user:add", type = PermissionType.BUTTON)
 * public void addUser(UserDTO dto) { ... }
 *
 * // 多按钮权限 AND 校验（必须同时拥有所有权限）
 * &#64;AuthMenuPermission(permissionCodes = {"sys:user:add", "sys:user:edit"}, type = PermissionType.BUTTON)
 * public void saveUser(UserDTO dto) { ... }
 *
 * // 多按钮权限 OR 校验（拥有其一即可）
 * &#64;AuthMenuPermission(permissionCodes = {"sys:user:delete", "sys:user:manage"},
 *     type = PermissionType.BUTTON, mode = PermissionMode.OR)
 * public void deleteUser(Long id) { ... }
 *
 * // 限定特定角色才能访问
 * &#64;AuthMenuPermission(roleCodes = "admin", permissionCodes = "sys:user:*", type = PermissionType.BUTTON)
 * public void deleteUser(Long id) { ... }
 * </pre>
 *
 * <p><b>权限码匹配规则：</b>
 *
 * <ul>
 *   <li>精确匹配：权限码完全一致即表示拥有该权限
 *   <li>通配符匹配：需开启 {@code wildcard-enabled=true}，支持 {@code *} 匹配任意字符
 *   <li>示例：{@code sys:user:*} 可匹配 {@code sys:user:add}、{@code sys:user:edit} 等
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AuthApiPermission
 * @see AuthRowPermission
 * @see AuthColPermission
 */
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthMenuPermission {

  /**
   * 限定角色编码集合。
   *
   * <p>若指定，则只有拥有指定角色的用户才能访问被注解标注的接口。 支持多角色配置，如 {@code {"admin", "manager"}}。 为空时表示不限制角色，但需要通过
   * {@link #permissionCodes()} 校验。
   *
   * @return 角色编码数组
   */
  String[] roleCodes() default {};

  /**
   * 必需的菜单/按钮权限码集合。
   *
   * <p>用户必须拥有列表中指定的权限码才能访问被注解标注的接口。 权限码建议与前端菜单/按钮的权限标识保持一致， 格式建议采用 {@code 领域:资源:操作} 的命名规范。
   *
   * @return 权限码数组
   */
  String[] permissionCodes() default {};

  /**
   * 权限类型。
   *
   * <ul>
   *   <li>{@link PermissionType#MENU}：菜单级别权限（默认）
   *   <li>{@link PermissionType#BUTTON}：按钮级别权限
   * </ul>
   *
   * @return 权限类型
   */
  PermissionType type() default PermissionType.MENU;

  /**
   * 多权限码的校验模式。
   *
   * <ul>
   *   <li>{@link PermissionMode#AND}：必须拥有全部权限码（默认）
   *   <li>{@link PermissionMode#OR}：拥有任意一个权限码即可
   * </ul>
   *
   * @return 校验模式
   */
  PermissionMode mode() default PermissionMode.AND;

  /**
   * 权限类型枚举。
   *
   * @since 26.09.01
   */
  enum PermissionType {
    /**
     * 菜单级别权限。
     *
     * <p>通常对应系统菜单树中的菜单项，用于控制用户可见的菜单入口。
     */
    MENU,

    /**
     * 按钮级别权限。
     *
     * <p>通常对应菜单下的操作按钮，用于控制用户可执行的操作。
     */
    BUTTON
  }
}
