package com.njydsz.common.domain.constant;

import com.njydsz.common.core.constant.HeaderConstants;

/**
 * Token相关常量类
 *
 * <p>定义JWT/OAuth2 Token的键名、过期时间等常量配置。
 *
 * <p><b>主要常量分类：</b>
 * <ul>
 *   <li>令牌标识：Authentication、补充令牌标识</li>
 *   <li>令牌前缀：如 "ydsz" 用于标识系统</li>
 *   <li>回调URL参数名</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TokenConstants {

    private TokenConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 令牌标识常量 ====================

    /** 令牌自定义标识键名 */
    public static final String AUTHENTICATION = "Authorization";

    /** 补充令牌自定义标识键名 */
    public static final String SUPPLY_AUTHORIZATION = HeaderConstants.X_ACCESS_TOKEN;

    // ==================== 令牌前缀常量 ====================

    /** 令牌前缀 */
    public static final String PREFIX = "ydsz";

    // ==================== 回调URL参数常量 ====================

    /** OAuth2回调地址参数名 */
    public static final String REDIRECT_URL = "redirect_url";
}
