package com.njydsz.pmis.common.web.filter;

import com.njydsz.pmis.common.safe.filter.BaseSecurityHeaderFilter;
import com.njydsz.pmis.common.safe.config.SecurityHeaderProperties;

/**
 * Web 端安全响应头过滤器
 *
 * <p>继承 {@link BaseSecurityHeaderFilter}，向 HTTP 响应中注入安全相关头部，
 * 防范 XSS、点击劫持、MIME 嗅探等常见 Web 攻击。
 *
 * <p><b>注入的安全头部：</b>
 * <ul>
 *   <li>X-XSS-Protection: 1; mode=block</li>
 *   <li>X-Content-Type-Options: nosniff</li>
 *   <li>Strict-Transport-Security: max-age=31536000; includeSubDomains</li>
 *   <li>X-Frame-Options: SAMEORIGIN</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseSecurityHeaderFilter
 * @see SecurityHeaderProperties
 */
public class SecurityHeaderFilter extends BaseSecurityHeaderFilter {

    /**
     * 构造安全响应头过滤器
     *
     * @param properties 安全头部配置属性
     */
    public SecurityHeaderFilter(SecurityHeaderProperties properties) {
        super(properties);
    }
}
