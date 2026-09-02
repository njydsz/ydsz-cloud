package com.njydsz.common.app.filter;

import com.njydsz.common.app.config.AppContentCacheProperties;
import com.njydsz.common.base.filter.AbstractContentCachingFilter;

/**
 * App 端请求体缓存过滤器
 *
 * <p>通过 {@link AppContentCacheProperties} 注入最大缓存大小（字节）， 默认 2MB，防止大文件上传场景下的 OOM。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AbstractContentCachingFilter
 * @see AppContentCacheProperties
 */
public class AppContentCachingFilter extends AbstractContentCachingFilter {

  /**
   * 构造方法
   *
   * <p>当配置值大于 0 时使用配置值，否则回退到基类默认值 {@code DEFAULT_CACHE_CAPACITY}。
   *
   * @param properties App 端内容缓存配置属性
   */
  public AppContentCachingFilter(AppContentCacheProperties properties) {
    super(properties.getMaxSize() > 0 ? properties.getMaxSize() : DEFAULT_CACHE_CAPACITY);
  }
}
