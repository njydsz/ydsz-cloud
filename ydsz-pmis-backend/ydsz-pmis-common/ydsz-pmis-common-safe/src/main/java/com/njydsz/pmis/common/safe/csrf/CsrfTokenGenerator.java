package com.njydsz.pmis.common.safe.csrf;

/**
 * CSRF 令牌生成器接口
 *
 * <p>定义 CSRF 令牌生成策略，支持自定义实现。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface CsrfTokenGenerator {

    /**
     * 生成 CSRF 令牌
     *
     * @param sessionId 会话 ID
     * @return CSRF 令牌
     */
    String generate(String sessionId);

    /**
     * 验证 CSRF 令牌
     *
     * @param token     待验证的令牌
     * @param sessionId 会话 ID
     * @return 是否有效
     */
    boolean validate(String token, String sessionId);
}
