package com.njydsz.pmis.common.core.constant;

/**
 * Token相关常量类
 *
 * <p>定义JWT/OAuth2 Token的键名、过期时间等常量配置。
 * 此类为最终类不可继承，所有常量均为静态final字段。
 *
 * <p><b>主要常量分类：</b>
 * <ul>
 *   <li>令牌标识：Authentication、补充令牌标识</li>
 *   <li>令牌前缀：如 "ydsz" 用于标识系统</li>
 *   <li>回调URL参数名</li>
 *   <li>Token过期时间：AccessToken 24小时，RefreshToken 7天</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see SecurityConstants
 */
public final class TokenConstants {

    /**
     * 私有构造函数
     *
     * <p>防止外部通过 new 关键字创建实例，确保此类作为纯常量类使用。
     *
     * @throws UnsupportedOperationException 始终抛出此异常
     */
    private TokenConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 令牌标识常量 ====================

    /**
     * 令牌自定义标识键名
     * <p>HTTP请求头中用于传递Bearer Token的键名，值为 "Authorization"
     */
    public static final String AUTHENTICATION = "Authorization";

    /**
     * 补充令牌自定义标识键名
     * <p>用于需要额外Token验证的场景，区别于标准Authorization头
     */
    public static final String SUPPLY_AUTHORIZATION = HeaderConstants.X_ACCESS_TOKEN;

    // ==================== 令牌前缀常量 ====================

    /**
     * 令牌前缀
     * <p>用于标识本系统发布的Token，如 "ydsz" + Token
     */
    public static final String PREFIX = "ydsz";

    // ==================== 回调URL参数常量 ====================

    /**
     * OAuth2回调地址参数名
     * <p>授权码模式中用于传递原始请求URL
     */
    public static final String REDIRECT_URL = "redirect_url";

}