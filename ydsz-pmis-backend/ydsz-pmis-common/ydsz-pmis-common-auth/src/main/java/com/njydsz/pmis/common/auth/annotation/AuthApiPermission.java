package com.njydsz.pmis.common.auth.annotation;

import java.lang.annotation.*;

/**
 * 接口权限校验注解。
 *
 * <p>用于标注在 Controller 或具体接口方法上，表示调用该接口所需的接口权限码。
 * 支持多角色和多权限码的组合校验，并提供 AND/OR 两种校验模式。
 *
 * <p><b>校验链路：</b>
 * <ol>
 *   <li>从请求头 X-Access-Token 获取 token</li>
 *   <li>根据 token 从 Redis 获取用户信息（含 roleCode）</li>
 *   <li>根据 roleCode 加载角色关联的接口权限集合</li>
 *   <li>按注解配置的 mode（AND/OR）进行权限码匹配</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 校验单个接口权限码
 * &#64;AuthApiPermission(apiCodes = "sys:user:list")
 * public UserVO getUser(Long id) { ... }
 *
 * // 校验多个接口权限码（OR 模式：满足其一即可）
 * &#64;AuthApiPermission(apiCodes = {"sys:user:view", "sys:user:manage"}, mode = Mode.OR)
 * public UserVO getUser(Long id) { ... }
 *
 * // 校验指定角色
 * &#64;AuthApiPermission(roleCodes = "admin", apiCodes = "sys:user:*")
 * public void deleteUser(Long id) { ... }
 * </pre>
 *
 * <p><b>权限码匹配规则：</b>
 * <ul>
 *   <li>精确匹配：权限码完全一致即表示拥有该权限</li>
 *   <li>通配符匹配：需开启 {@code wildcard-enabled=true}，支持 {@code *} 匹配任意字符</li>
 *   <li>示例：{@code sys:user:*} 可匹配 {@code sys:user:add}、{@code sys:user:delete} 等</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 * @see AuthMenuPermission
 * @see AuthRowPermission
 * @see AuthColPermission
 */
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthApiPermission {

    /**
     * 限定角色编码集合。
     *
     * <p>若指定，则只有拥有指定角色的用户才能访问被注解标注的接口。
     * 支持多角色配置，如 {@code {"admin", "manager"}}。
     * 为空时表示不限制角色，但需要通过 {@link #apiCodes()} 校验。
     *
     * @return 角色编码数组
     */
    String[] roleCodes() default {};

    /**
     * 必需的接口权限码集合。
     *
     * <p>用户必须拥有列表中指定的权限码才能访问被注解标注的接口。
     * 权限码格式建议采用 {@code 领域:资源:操作} 的命名规范，
     * 如 {@code sys:user:add} 表示系统模块-用户资源-新增操作。
     *
     * @return 接口权限码数组
     */
    String[] apiCodes() default {};

    /**
     * 多权限码的校验模式。
     *
     * <ul>
     *   <li>{@link Mode#AND}：必须拥有全部权限码（默认）</li>
     *   <li>{@link Mode#OR}：拥有任意一个权限码即可</li>
     * </ul>
     *
     * @return 校验模式
     */
    PermissionMode mode() default PermissionMode.AND;

    /**
     * API 路径模式，用于 URL 路径级别的权限控制。
     *
     * <p>与 {@link #apiCodes()} 互补：{@code apiCodes} 基于权限码校验，
     * {@code pathPatterns} 基于 URL 路径模式匹配（支持 Ant 风格通配符）。
     * 例如：{@code "/api/user/**"} 匹配所有 /api/user/ 下的接口。
     *
     * <p>当 {@code pathPatterns} 和 {@code apiCodes} 同时配置时，
     * 遵循 {@link #mode()} 指定的 AND/OR 逻辑。
     *
     * @return API 路径模式数组
     */
    String[] pathPatterns() default {};

}