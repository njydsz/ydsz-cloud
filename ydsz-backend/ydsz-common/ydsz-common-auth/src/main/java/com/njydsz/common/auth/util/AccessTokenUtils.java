package com.njydsz.common.auth.util;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.util.auth.AuthInfoUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * AccessToken 获取工具类。
 *
 * <p>优先级：
 * <ol>
 *   <li>{@link AuthInfoUtils#getAccessToken()} 从上下文中获取</li>
 *   <li>Spring RequestContextHolder 当前请求的 Header</li>
 * </ol>
 *
 * <p>提供 Token 格式校验能力，支持 JWT 和 Bearer Token 格式验证。
 *
 * @since 1.0.0
 * 
 */
public class AccessTokenUtils {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenUtils.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private AccessTokenUtils() {
    }

    /**
     * 解析当前请求 AccessToken。
     *
     * <p>增加类型校验，确保 RequestAttributes 为 ServletRequestAttributes 类型。
     *
     * @return AccessToken（可能为空）
     */
    public static String resolve() {
        String accessToken = AuthInfoUtils.getAccessToken();
        if (StringUtils.isNotBlank(accessToken)) {
            return accessToken;
        }

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            log.debug("RequestAttributes 为空，无法获取 Token");
            return null;
        }

        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            log.warn("RequestAttributes 类型不匹配: {}, 期望 ServletRequestAttributes",
                    requestAttributes.getClass().getName());
            return null;
        }

        ServletRequestAttributes sra = (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = sra.getRequest();
        accessToken = request.getHeader(HeaderConstants.X_ACCESS_TOKEN);
        if (StringUtils.isNotBlank(accessToken)) {
            return accessToken;
        }
        return null;
    }

    /**
     * 校验 Token 格式是否合法。
     *
     * <p>支持以下格式校验：
     * <ul>
     *   <li>JWT 格式: 包含两个 "." 分隔符（header.payload.signature）</li>
     *   <li>Bearer Token: 以 "Bearer " 开头</li>
     * </ul>
     *
     * @param token 待校验的 Token
     * @return 格式合法返回 true，否则返回 false
     */
    public static boolean validateTokenFormat(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }

        String trimmed = token.trim();

        if (trimmed.startsWith(BEARER_PREFIX)) {
            String actualToken = trimmed.substring(BEARER_PREFIX.length());
            if (StringUtils.isBlank(actualToken)) {
                return false;
            }
            return isJwtFormat(actualToken);
        }

        return isJwtFormat(trimmed);
    }

    /**
     * 判断是否为 JWT 格式。
     *
     * <p>JWT 格式包含三个部分，由两个 "." 分隔符分隔：
     * <ul>
     *   <li>Header: Base64 编码的头部</li>
     *   <li>Payload: Base64 编码的载荷</li>
     *   <li>Signature: Base64 编码的签名</li>
     * </ul>
     *
     * @param token Token 字符串
     * @return 是 JWT 格式返回 true，否则返回 false
     */
    private static boolean isJwtFormat(String token) {
        int dotCount = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '.') {
                dotCount++;
            }
        }
        return dotCount == 2;
    }
}
