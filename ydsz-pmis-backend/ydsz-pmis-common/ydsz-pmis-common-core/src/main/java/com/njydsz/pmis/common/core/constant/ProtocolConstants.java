package com.njydsz.pmis.common.core.constant;

/**
 * 协议常量
 *
 * <p>定义系统中使用的协议前缀常量，包括 RMI/LDAP 查找协议和 HTTP/HTTPS 网络协议。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
