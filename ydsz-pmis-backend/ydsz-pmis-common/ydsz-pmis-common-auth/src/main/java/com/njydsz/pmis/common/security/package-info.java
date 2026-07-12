/**
 * 安全相关领域类层。
 *
 * <p>集中定义所有"安全 / 合规"相关的领域模型 / 工具类，包括：
 * <ul>
 *   <li>登录上下文（{@code SecurityContext}）与登录用户（{@code LoginUser}）</li>
 *   <li>多租户上下文（{@code TenantContext}）</li>
 *   <li>数据范围上下文（{@code DataScopeContext}）与数据范围助手（{@code DataScopeHelper}）</li>
 *   <li>CSRF 安全策略（{@code CsrfSecurityPolicy}）</li>
 *   <li>密码策略（{@code PasswordPolicy}）与 TOTP（{@code TotpUtil}）</li>
 *   <li>登录状态（{@code LoginStatus}）与账号锁定信息（{@code AccountLockInfo}）</li>
 *   <li>审计事件（登录审计 / 敏感操作 / 数据导出 / 账号锁定）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>安全相关常量 / 策略 / 工具不得分散在业务模块，统一在本包集中维护</li>
 *   <li>所有上下文（Context）使用 {@code ThreadLocal} 透传，请求结束由
 *       {@code AuthInterceptor} / 过滤器清理，避免线程池污染</li>
 *   <li>密码学操作（哈希 / 加密 / 签名）使用 {@code com.njydsz.pmis.common.util.CryptoUtil}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.security;
