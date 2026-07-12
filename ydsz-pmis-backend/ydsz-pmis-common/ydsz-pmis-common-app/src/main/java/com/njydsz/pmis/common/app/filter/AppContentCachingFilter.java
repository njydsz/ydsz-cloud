package com.njydsz.pmis.common.app.filter;

import com.njydsz.pmis.common.base.filter.AbstractContentCachingFilter;
import org.springframework.beans.factory.annotation.Value;

/**
 * App 端请求体缓存过滤器
 *
 * <p>通过配置 {@code pmis.app.content-cache.max-size} 控制最大缓存大小（字节），
 * 默认 2MB，防止大文件上传场景下的 OOM。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AppContentCachingFilter extends AbstractContentCachingFilter {

    @Value("${pmis.app.content-cache.max-size:2097152}")
    private int maxCacheSize;

    @Override
    protected int getCacheCapacity() {
        return maxCacheSize > 0 ? maxCacheSize : DEFAULT_CACHE_CAPACITY;
    }
}
