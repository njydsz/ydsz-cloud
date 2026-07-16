package com.njydsz.common.app.filter;

import com.njydsz.common.base.filter.RequestContextCleanupFilter;

/**
 * App 端请求上下文清理过滤器
 *
 * <p>继承 {@link RequestContextCleanupFilter}，在请求处理完成后清理
 * {@link com.njydsz.common.util.auth.RequestHolder} 中的请求级上下文，
 * 防止线程复用导致的信息泄露。
 *
 * <p><b>线程安全性：</b>依赖于基类实现，本身无状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.0.0
 */
public class AppRequestContextCleanupFilter extends RequestContextCleanupFilter {
}
