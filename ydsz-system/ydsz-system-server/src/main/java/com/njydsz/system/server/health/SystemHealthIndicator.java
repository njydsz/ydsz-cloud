package com.njydsz.system.server.health;

import com.njydsz.common.redis.health.RedisHealthIndicator;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.system.infra.mapper.ConfigMapper;
import com.njydsz.system.infra.mapper.DictItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;

/**
 * 系统模块健康检查 Indicator
 *
 * <p>Spring Boot Actuator 的 {@link HealthIndicator} 实现，承载 {@code ydsz-system} 微服务的健康检查能力。
 * 通过轻量级探针快速判断服务依赖（Redis / DB）的可达性，<b>避免全表 COUNT 扫描</b>等重型操作阻塞监控。
 *
 * <p><b>检查项：</b>
 *
 * <ul>
 *   <li><b>Redis 连通性</b> — 通过 {@link RedisCallback} 执行 {@code PING} 命令， 验证 Redis 服务可达且响应正常
 *   <li><b>配置表可达性</b> — 通过 {@code configMapper.selectByConfigKey("__health_probe__")} 探针 SQL 验证
 *       {@code ydsz_config} 表可读
 *   <li><b>字典表可达性</b> — 通过 {@code dictItemMapper.selectByTypeAndCode(...)} 探针 验证 {@code
 *       ydsz_dict_item} 表可读
 * </ul>
 *
 * <p><b>启用条件：</b>
 *
 * <ul>
 *   <li>{@code @ConditionalOnClass(HealthIndicator.class)} — Spring Boot Actuator 存在时启用
 *   <li>{@code @ConditionalOnProperty(prefix = "ydsz.system", name = "health-enabled", havingValue
 *       = "true", matchIfMissing = true)} — 配置开关默认开启
 * </ul>
 *
 * <p><b>性能考量：</b>
 *
 * <ul>
 *   <li>使用占位符（{@code __health_probe__}）作为探针参数，<b>命中索引但不返回数据</b>， 避免 COUNT(*) 扫描
 *   <li>{@link com.njydsz.common.web.health.AbstractModuleHealthIndicator#checkTableProbe} 框架
 *       自动捕获异常并转换为 {@code Health.down()}，不会因探针失败抛出未处理异常
 *   <li>整体执行耗时应 < 50ms（受 Redis / DB 网络延迟影响）
 * </ul>
 *
 * <p><b>访问端点：</b>{@code GET /actuator/health/system}（由 Actuator 自动暴露）
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see org.springframework.boot.health.contributor.HealthIndicator Spring Boot 健康检查接口
 * @see com.njydsz.common.web.health.AbstractModuleHealthIndicator 通用健康检查基类
 * @see org.springframework.data.redis.core.RedisTemplate Redis 模板
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(
    prefix = "ydsz.system",
    name = "health-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class SystemHealthIndicator extends AbstractModuleHealthIndicator {

  /** Redis 健康检查（由 common-redis 提供） */
  private final ObjectProvider<RedisHealthIndicator> redisHealthIndicatorProvider;

  /** 配置表 Mapper（用于配置表探针） */
  private final ConfigMapper configMapper;

  /** 字典项 Mapper（用于字典表探针） */
  private final DictItemMapper dictItemMapper;

  /**
   * 执行系统模块健康检查
   *
   * <p>按顺序检查：① Redis 连通性 → ② 配置表可达性 → ③ 字典表可达性。 任意一项失败，整体健康状态降级为 {@code DOWN}，但<b>不会中断</b>后续检查项。
   *
   * <p>所有检查结果（耗时、状态、错误信息）写入 {@link Health.Builder}， 由 Actuator 在 {@code /actuator/health} 端点统一返回。
   */
  @Override
  protected void doHealthCheck(Health.Builder builder) {
    RedisHealthIndicator redisHealth = redisHealthIndicatorProvider.getIfAvailable();
    if (redisHealth != null) {
      Health redisResult = redisHealth.health();
      builder.withDetail("redis", redisResult.getStatus().getCode().toUpperCase());
      redisResult.getDetails().forEach((k, v) -> builder.withDetail("redis." + k, v));
      if (!redisResult.getStatus().equals(org.springframework.boot.health.contributor.Status.UP)) {
        builder.down();
      }
    } else {
      checkRedisNotConfigured(builder);
    }
    checkTableProbe(builder, "config", () -> configMapper.selectByConfigKey("__health_probe__"));
    checkTableProbe(
        builder,
        "dict",
        () -> dictItemMapper.selectByTypeAndCode("__health_probe__", "__health_probe__"));
  }
}
