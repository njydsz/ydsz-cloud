/**
 * JWT 令牌层。
 *
 * <p>封装 JWT（JSON Web Token）的签发 / 解析 / 校验逻辑。
 * PMIS 平台采用"双 Token"机制：访问 Token（短时效，放 Header）+ 刷新 Token（长时效，仅刷新接口使用）。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.token.JwtTokenProvider} - JWT 签发 / 解析 / 校验</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>Token 不存放敏感信息（密码 / 身份证 / 手机号），仅放 userId / username / roles / tenantId</li>
 *   <li>签名密钥通过 {@code SecretManager} 获取，禁止硬编码</li>
 *   <li>Token 吊销通过 Redis 黑名单（{@code @PrePermission} 切面统一拦截）</li>
 *   <li>解析失败统一抛 {@code BizException(TOKEN_INVALID)}，由 {@code GlobalExceptionHandler} 统一 401 响应</li>
 * </ul>
 *
 * @author ydyz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.token;
