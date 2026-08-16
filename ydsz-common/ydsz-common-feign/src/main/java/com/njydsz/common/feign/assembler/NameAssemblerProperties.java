package com.njydsz.common.feign.assembler;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 名称富化组件配置属性。
 *
 * <p>配置前缀：ydsz.feign.name-assembler
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.feign.name-assembler")
public class NameAssemblerProperties {

  /** 是否启用 NameAssembler，默认 true */
  private boolean enabled = true;

  /** 本地缓存最大条目数，默认 1000 */
  private int cacheMaxSize = 1000;

  /** 本地缓存过期时间，默认 5 分钟 */
  private Duration cacheTtl = Duration.ofMinutes(5);

  /** 是否启用 Redis 二级缓存，默认 false */
  private boolean redisCacheEnabled = false;

  /** Redis 二级缓存 TTL，默认 10 分钟 */
  private Duration redisCacheTtl = Duration.ofMinutes(10);

  /** Feign 调用超时时间（毫秒），默认 3000 */
  private int feignTimeoutMs = 3000;

  /** 批量查询最大 ID 数量，默认 100 */
  private int batchMaxSize = 100;

  /** Feign 失败时是否用 ID 字符串顶替名称字段，默认 true */
  private boolean fallbackToId = true;
}
