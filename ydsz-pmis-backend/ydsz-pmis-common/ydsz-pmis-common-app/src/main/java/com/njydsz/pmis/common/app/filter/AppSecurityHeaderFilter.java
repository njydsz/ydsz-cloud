package com.njydsz.pmis.common.app.filter;

import com.njydsz.pmis.common.safe.filter.BaseSecurityHeaderFilter;
import com.njydsz.pmis.common.safe.config.SecurityHeaderProperties;

/**
 * App 端安全响应头过滤器
 *
 * <p>继承 {@link BaseSecurityHeaderFilter}，根据 {@link SecurityHeaderProperties}
 * 为响应附加常见的安全响应头（如 {@code X-Content-Type-Options}、
 * {@code X-Frame-Options}、{@code Strict-Transport-Security} 等）。
 *
 * <p><b>线程安全性：</b>无状态过滤器，线程安全。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see SecurityHeaderProperties
 */
public class AppSecurityHeaderFilter extends BaseSecurityHeaderFilter {

    /**
     * 构造方法
     *
     * @param properties 安全响应头配置
     */
    public AppSecurityHeaderFilter(SecurityHeaderProperties properties) {
        super(properties);
    }
}
