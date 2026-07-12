package com.njydsz.pmis.common.app.filter;

import com.njydsz.pmis.common.base.filter.RequestContextCleanupFilter;

/**
 * App 端请求上下文清理过滤器
 *
 * <p>继承 {@link RequestContextCleanupFilter}，在请求处理完成后清理请求级上下文。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AppRequestContextCleanupFilter extends RequestContextCleanupFilter {
}
