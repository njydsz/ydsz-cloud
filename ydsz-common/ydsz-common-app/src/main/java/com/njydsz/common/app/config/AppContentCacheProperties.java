package com.njydsz.common.app.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * App 端请求体缓存配置属性
 *
 * <p>控制请求体缓存过滤器的最大缓存容量，防止大文件上传场景下的 OOM 风险。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.app.content-cache")
public class AppContentCacheProperties {

  /**
   * 最大缓存字节数，默认 2MB
   *
   * <p>超过此值的请求体内容将被截断丢弃，不会缓存。 对于典型 JSON 请求体，2MB 足够；如需支持更大请求体，可适当调大此值。
   */
  @Min(0)
  private int maxSize = 2 * 1024 * 1024;

  /**
   * 获取最大缓存字节数
   *
   * @return 最大缓存字节数
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * 设置最大缓存字节数
   *
   * @param maxSize 最大缓存字节数
   */
  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }
}
