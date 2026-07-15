package com.njydsz.pmis.common.auth.service;

import java.util.Map;

import com.njydsz.pmis.common.auth.model.UserInfo;
import com.njydsz.pmis.common.auth.service.impl.RedisRbacUserInfoService;

/**
 * RBAC 用户信息加载器接口。
 *
 * <p>定义根据 accessToken 加载用户信息的行为。
 * 用户信息必须包含 {@code roleCode} 字段，用于后续权限解析。
 *
 * <p><b>实现类职责：</b>
 * <ul>
 *   <li>从请求上下文（ThreadLocal/请求头）获取 accessToken</li>
 *   <li>根据 accessToken 加载用户信息（可来自 Redis/DB/远程服务）</li>
 *   <li>确保返回的 UserInfo 中包含 {@code roleCode} 字段</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 默认实现：基于 Redis
 * &#64;Bean
 * public RbacUserInfoService rbacUserInfoService(RedisService redisService) {
 *     return new RedisRbacUserInfoService(redisService);
 * }
 *
 * // 自定义实现：来自数据库
 * &#64;Bean
 * public RbacUserInfoService rbacUserInfoService(UserService userService) {
 *     return new DbRbacUserInfoService(userService);
 * }
 * </pre>
 *
 * @since 1.0.0
 * 
 * @see RedisRbacUserInfoService
 * @see UserInfo
 */
public interface RbacUserInfoService {

    /**
     * 根据 accessToken 加载用户信息（类型安全）
     *
     * <p>推荐使用此方法，返回类型安全的 UserInfo DTO，
     * 避免 Map&lt;String, Object&gt; 带来的类型转换错误和安全隐患。</p>
     *
     * @param accessToken 访问令牌
     * @return 用户信息；若 token 无效返回 null
     */
    UserInfo loadUserInfo(String accessToken);

    /**
     * 根据 accessToken 加载用户信息 Map（内部使用）
     *
     * <p>此方法仅供内部实现使用，外部调用请使用 {@link #loadUserInfo(String)}。</p>
     *
     * @param accessToken 访问令牌
     * @return 用户信息 Map；若 token 无效返回空 Map
     */
    Map<String, Object> loadUserInfoMap(String accessToken);

    /**
     * 获取当前请求的 accessToken。
     *
     * <p>从 ThreadLocal 或请求上下文获取当前请求的 token。
     *
     * @return token；若无法获取返回 null
     */
    String loadCurrentToken();

}
