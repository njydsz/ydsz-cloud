package com.njydsz.common.web.health;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 模块健康检查抽象基类。
 *
 * <p>提供统一的模板方法模式和辅助方法，消除各模块 HealthIndicator 中的重复代码。 子类只需覆写 {@link #doHealthCheck(Health.Builder)}
 * 添加模块特有探针。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Component
 * public class MyModuleHealthIndicator extends AbstractModuleHealthIndicator {
 *     private final RedisStringOps redisStringOps;
 *     private final MyMapper myMapper;
 *
 *     @Override
 *     protected void doHealthCheck(Health.Builder builder) {
 *         checkRedis(builder, () -> redisStringOps.hasKey("health-check"));
 *         checkTableProbe(builder, "myTable", () -> myMapper.selectById(1L));
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractModuleHealthIndicator implements HealthIndicator {

  @Override
  public final Health health() {
    Health.Builder builder = new Health.Builder();
    try {
      doHealthCheck(builder);
    } catch (Exception e) {
      log.warn("[Health] 健康检查异常: {}", e.getMessage(), e);
      builder.down().withDetail("error", extractMessage(e));
    }
    return builder.build();
  }

  /**
   * 子类实现具体的健康检查逻辑，通过 builder 收集检查结果。
   *
   * @param builder 健康状态构建器
   */
  protected abstract void doHealthCheck(Health.Builder builder);

  // ──────────────────── Redis 检查辅助 ────────────────────

  /**
   * 检查 Redis 连通性。
   *
   * <p>调用方提供 PING 操作（通常 {@code () -> redisStringOps.hasKey("health-check")}）， 基类统一处理成功/失败状态。
   *
   * @param builder 健康状态构建器
   * @param pingQuery PING 操作，返回 pong 字符串
   */
  protected void checkRedis(Health.Builder builder, Supplier<String> pingQuery) {
    try {
      String ping = pingQuery.get();
      builder.withDetail("redis", "UP - " + ping);
    } catch (Exception e) {
      builder.withDetail("redis", "DOWN - " + extractMessage(e));
      builder.down();
    }
  }

  /** 标记 Redis 未配置（可选依赖场景）。 */
  protected void checkRedisNotConfigured(Health.Builder builder) {
    builder.withDetail("redis", "UNKNOWN - not configured");
  }

  // ──────────────────── DB 探针检查辅助 ────────────────────

  /**
   * 轻量级数据库表可达性探针。
   *
   * <p>执行一个轻量查询，成功则标记 UP，失败则标记 DOWN。
   *
   * @param builder 健康状态构建器
   * @param probeName 探针名称（如 "configTable"、"userTable"）
   * @param probeQuery 轻量查询
   */
  protected void checkTableProbe(Health.Builder builder, String probeName, Runnable probeQuery) {
    try {
      probeQuery.run();
      builder.withDetail(probeName, "UP - table reachable");
    } catch (Exception e) {
      builder.withDetail(probeName, "DOWN - " + extractMessage(e));
      builder.down();
    }
  }

  /**
   * 带返回值的轻量级数据库表可达性探针。
   *
   * @param builder 健康状态构建器
   * @param probeName 探针名称
   * @param probeQuery 轻量查询，返回结果会写入 details
   */
  protected <T> void checkTableProbeWithValue(
      Health.Builder builder, String probeName, Callable<T> probeQuery) {
    try {
      T value = probeQuery.call();
      builder.withDetail(probeName, "UP - " + value);
    } catch (Exception e) {
      builder.withDetail(probeName, "DOWN - " + extractMessage(e));
      builder.down();
    }
  }

  // ──────────────────── 可选组件检查辅助 ────────────────────

  /**
   * 安全地添加可选组件的状态信息。组件为 null 时标记 NOT_CONFIGURED。
   *
   * @param builder 健康状态构建器
   * @param componentName 组件名称
   * @param component 组件实例（可为 null）
   * @param statusChecker 状态检查逻辑
   */
  protected void checkOptionalComponent(
      Health.Builder builder,
      String componentName,
      Object component,
      Function<Object, Map<String, Object>> statusChecker) {
    if (component == null) {
      builder.withDetail(componentName, "NOT_CONFIGURED");
      return;
    }
    try {
      Map<String, Object> details = statusChecker.apply(component);
      builder.withDetail(componentName, details);
    } catch (Exception e) {
      builder.withDetail(componentName, "ERROR - " + extractMessage(e));
    }
  }

  /** 安全地添加可选组件的简单状态。组件为 null 时标记 NOT_CONFIGURED。 */
  protected void checkOptionalStatus(
      Health.Builder builder,
      String componentName,
      Object component,
      Supplier<String> statusSupplier) {
    if (component == null) {
      builder.withDetail(componentName, "NOT_CONFIGURED");
      return;
    }
    try {
      builder.withDetail(componentName, statusSupplier.get());
    } catch (Exception e) {
      builder.withDetail(componentName, "ERROR - " + extractMessage(e));
    }
  }

  // ──────────────────── 通用辅助方法 ────────────────────

  /** 提取异常信息（仅取 message，避免堆栈污染 details）。 */
  protected String extractMessage(Exception e) {
    Throwable cause = e.getCause();
    if (cause != null) {
      return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
    return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
  }
}
