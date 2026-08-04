package com.njydsz.common.web.filter;

import com.njydsz.common.base.filter.AbstractContentCachingFilter;
import com.njydsz.common.web.config.WebContentCacheProperties;

/**
 * Web 端请求体缓存过滤器
 *
 * <p>继承 {@link AbstractContentCachingFilter}，将请求包装为
 * {@code ContentCachingRequestWrapper}，支持请求体多次读取。
 * 主要用于 XSS 过滤器、请求日志拦截器等需要重复读取请求体的场景。
 *
 * <p>通过 {@link WebContentCacheProperties} 配置最大缓存大小（字节），
 * 默认 2MB，防止大文件上传场景下的 OOM。
 *
 * @author ydsz-team
 * @see AbstractContentCachingFilter
 * @see WebContentCacheProperties
 * @since 1.0.0
 */
public class ContentCachingFilter extends AbstractContentCachingFilter {

    private final WebContentCacheProperties properties;

    public ContentCachingFilter(WebContentCacheProperties properties) {
        this.properties = properties;
    }

    @Override
    protected int getCacheCapacity() {
        int maxSize = properties.getMaxSize();
        return maxSize > 0 ? maxSize : DEFAULT_CACHE_CAPACITY;
    }
}
