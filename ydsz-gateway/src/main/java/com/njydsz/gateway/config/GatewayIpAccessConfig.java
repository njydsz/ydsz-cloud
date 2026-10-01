package com.njydsz.gateway.config;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.safe.cache.SafeCacheFactoryHelper;
import com.njydsz.common.safe.config.IpAccessProperties;
import com.njydsz.common.safe.ip.IpAccessService;

/**
 * 网关 IP 访问控制服务配置。
 *
 * <p>响应式网关（WebFlux）栈不包含 servlet API，导致 {@code SafeConfiguration}（标注
 * {@code @ConditionalOnClass(FilterRegistrationBean.class)}）无法加载，其内部注册的
 * {@link IpAccessService} Bean 在网关容器中缺失。
 *
 * <p>本配置作为网关侧兜底注册：当 {@code ydsz.safe.ip-access.enabled=true} 且 {@link RedisStringOps}
 * Bean 可用时，创建 {@link IpAccessService} 实例，供 {@code IpAccessGlobalFilter} 使用。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>
 * ydsz:
 *   safe:
 *     ip-access:
 *       enabled: true
 *       mode: BLACKLIST
 *       static-blacklist:
 *         - 10.0.0.99
 *   gateway:
 *     filter:
 *       ip-access:
 *         enabled: true
 * </pre>
 *
 * @since 26.09.30
 * @author ydsz-team
 */
@Configuration
@EnableConfigurationProperties(IpAccessProperties.class)
public class GatewayIpAccessConfig {

  private static final Logger LOG = LoggerFactory.getLogger(GatewayIpAccessConfig.class);

  /**
   * 注册 IP 访问控制服务 Bean（网关侧兜底注册）。
   *
   * <p>补充 SafeConfiguration 在 WebFlux 环境下无法注册的 {@link IpAccessService}。
   * 条件：
   * <ul>
   *   <li>{@code ydsz.safe.ip-access.enabled=true}</li>
   *   <li>{@link RedisStringOps} Bean 可用</li>
   * </ul>
   *
   * @param properties IP 访问控制配置
   * @param redisStringOps Redis String 操作
   * @return {@link IpAccessService} 实例
   */
  @Bean
  @ConditionalOnMissingBean(IpAccessService.class)
  @ConditionalOnBean(RedisStringOps.class)
  @ConditionalOnProperty(prefix = "ydsz.safe.ip-access", name = "enabled", havingValue = "true")
  public IpAccessService ipAccessService(
      IpAccessProperties properties, RedisStringOps redisStringOps) {
    LOG.info("[GatewayIpAccess] 注册 IP 访问控制服务: mode={}", properties.getMode());
    return new IpAccessService(
        properties,
        redisStringOps,
        SafeCacheFactoryHelper.createCache(
            properties.getLocalCacheTtlSeconds(),
            TimeUnit.SECONDS,
            properties.getLocalCacheSize()));
  }
}
