package com.njydsz.common.core.constant;

/**
 * 协议常量
 *
 * <p>定义系统中使用的协议前缀常量，包括 RMI/LDAP 查找协议和 HTTP/HTTPS 网络协议。
 *
 * <p><b>预期用途：</b>主要用于安全防护场景（如 Log4j JNDI 注入防护），
 * 通过校验用户输入是否包含 {@link #LOOKUP_RMI}、{@link #LOOKUP_LDAP}、{@link #LOOKUP_LDAPS}
 * 等危险协议前缀来阻断 JNDI 注入攻击。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** RMI 协议查找前缀 */
    public static final String LOOKUP_RMI = "rmi:";

    /** LDAP 协议查找前缀 */
    public static final String LOOKUP_LDAP = "ldap:";

    /** LDAPS 协议查找前缀（LDAP over SSL） */
    public static final String LOOKUP_LDAPS = "ldaps:";

    /** HTTP 协议前缀 */
    public static final String HTTP = "http://";

    /** HTTPS 协议前缀（HTTP over SSL/TLS） */
    public static final String HTTPS = "https://";
}
