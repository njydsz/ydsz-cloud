package com.njydsz.pmis.common.app.filter;

import com.njydsz.pmis.common.base.filter.AbstractContentCachingFilter;
import org.springframework.beans.factory.annotation.Value;

/**
 * App 端请求体缓存过滤器
 *
 * <p>通过配置 {@code ydsz.app.content-cache.max-size} 控制最大缓存大小（字节），
 * 默认 2MB，防止大文件上传场景下的 OOM。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see AbstractContentCachingFilter
 */
public class AppContentCachingFilter extends AbstractContentCachingFilter {

    /**
     * 最大缓存字节数，默认 2MB
     *
     * <p>防止大文件上传场景下的 OOM 风险；超过此值的内容将被截断丢弃。
     */
    @Value("${ydsz.app.content-cache.max-size:2097152}")
    private int maxCacheSize;

    /**
     * 返回当前过滤器应使用的缓存容量
     *
     * <p>当配置值大于 0 时使用配置值，否则回退到基类默认值 {@code DEFAULT_CACHE_CAPACITY}。
     *
     * @return 缓存容量（字节）
     */
    @Override
    protected int getCacheCapacity() {
        return maxCacheSize > 0 ? maxCacheSize : DEFAULT_CACHE_CAPACITY;
    }
}
