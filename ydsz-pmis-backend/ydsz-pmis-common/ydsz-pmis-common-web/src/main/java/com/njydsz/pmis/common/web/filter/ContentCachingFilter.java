package com.njydsz.pmis.common.web.filter;

import com.njydsz.pmis.common.base.filter.AbstractContentCachingFilter;
import org.springframework.beans.factory.annotation.Value;

/**
 * Web 端请求体缓存过滤器
 *
 * <p>继承 {@link AbstractContentCachingFilter}，将请求包装为
 * {@code ContentCachingRequestWrapper}，支持请求体多次读取。
 * 主要用于 XSS 过滤器、请求日志拦截器等需要重复读取请求体的场景。
 *
 * <p>通过配置 {@code remi.web.content-cache.max-size} 控制最大缓存大小（字节），
 * 默认 2MB，防止大文件上传场景下的 OOM。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see AbstractContentCachingFilter
 */
public class ContentCachingFilter extends AbstractContentCachingFilter {

    /**
     * 最大缓存字节数，默认 2MB
     */
    @Value("${remi.web.content-cache.max-size:2097152}")
    private int maxCacheSize;

    @Override
    protected int getCacheCapacity() {
        return maxCacheSize > 0 ? maxCacheSize : DEFAULT_CACHE_CAPACITY;
    }
}
