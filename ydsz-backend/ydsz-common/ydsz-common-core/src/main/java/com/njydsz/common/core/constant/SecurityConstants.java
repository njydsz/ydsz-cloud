package com.njydsz.common.core.constant;

/**
 * 安全相关常量类
 *
 * <p>定义系统安全相关的配置常量，包括密钥配置、密码加密、登录限制、安全头部等。
 * 此类为最终类不可继承，所有常量均为静态final字段。
 *
 * <p><b>主要常量分类：</b>
 * <ul>
 *   <li>密钥配置属性名：JWT、RSA 密钥对应的配置文件属性名</li>
 *   <li>密码加密：BCrypt 编码强度</li>
 *   <li>登录限制：最大尝试次数、锁定时长</li>
 *   <li>安全头部：XSS防护、Content-TypeOptions、HSTS等</li>
 *   <li>CSRF防护：头部和参数名称</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see TokenConstants
 */
public final class SecurityConstants {

    /**
     * 私有构造函数
     *
     * <p>防止外部通过 new 关键字创建实例，确保此类作为纯常量类使用。
     *
     * @throws UnsupportedOperationException 始终抛出此异常
     */
    private SecurityConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 密钥配置属性名 ====================

    /**
     * JWT密钥配置文件属性名
     * <p>对应 application.yml 中的 jwt.secret 配置项
     */
    public static final String JWT_SECRET_PROPERTY = "jwt.secret";

    /**
     * RSA私钥配置文件属性名
     * <p>对应 application.yml 中的 rsa.private-key 配置项
     */
    public static final String RSA_PRIVATE_KEY_PROPERTY = "rsa.private-key";

    /**
     * RSA公钥配置文件属性名
     * <p>对应 application.yml 中的 rsa.public-key 配置项
     */
    public static final String RSA_PUBLIC_KEY_PROPERTY = "rsa.public-key";

    // ==================== 密码加密配置 ====================

    /**
     * BCrypt 密码编码器强度
     * <p>值越高安全性越高，但计算成本也越高。建议范围 10-13
     */
    public static final int BCRYPT_ENCODER_STRENGTH = 12;

    // ==================== 安全响应头部 ====================

    /**
     * XSS防护头部
     * <p>用于启用浏览器XSS过滤器，值为 "1; mode=block"
     * <p>标准 HTTP 头，直接使用字符串字面量
     */
    public static final String SECURITY_HEADER_XSS_PROTECTION = "X-XSS-Protection";

    /**
     * Content-Type选项头部
     * <p>防止浏览器MIME类型嗅探，值为 "nosniff"
     * <p>标准 HTTP 头，直接使用字符串字面量
     */
    public static final String SECURITY_HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    /**
     * 严格传输安全头部
     * <p>HSTS头部，强制HTTPS连接
     * <p>标准 HTTP 头，直接使用字符串字面量
     */
    public static final String SECURITY_HEADER_STRICT_TRANSPORT = "Strict-Transport-Security";

    // ==================== CSRF防护配置 ====================

    /**
     * CSRF令牌请求头名称
     */
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";

    /**
     * CSRF令牌请求参数名称
     */
    public static final String CSRF_PARAMETER_NAME = "_csrf";

}
