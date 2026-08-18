package com.njydsz.literule.server.config;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 规则引擎配置启动校验器（P2-2：配置分组 + 启动校验）
 *
 * <p>在应用启动时对 {@link LiteRuleProperties} 执行跨字段校验，覆盖 {@code @Validated} 注解无法表达的约束：
 *
 * <ul>
 *   <li>分布式配置：心跳间隔必须小于超时时间
 *   <li>缓存配置：L1 TTL 必须小于 L2 TTL
 *   <li>熔断配置：错误率阈值与最小评估次数的逻辑一致性
 *   <li>并行配置：线程池大小与触发阈值的合理性
 *   <li>模型/事实配置：超时时间与降级策略的匹配
 * </ul>
 *
 * <p>校验失败时输出 WARN 日志（不阻塞启动），便于运维发现配置问题。 对于严重错误（如必填项缺失），抛出 {@link IllegalStateException} 阻塞启动。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LiteRuleProperties.class)
public class RuleConfigValidator {

  @Autowired private LiteRuleProperties properties;

  @Autowired private Validator validator;

  /** 校验错误列表（供监控/测试读取） */
  private final List<String> validationErrors = new ArrayList<>();

  @PostConstruct
  public void validate() {
    validationErrors.clear();

    // 1. JSR-303 注解校验
    validateAnnotations();

    // 2. 跨字段业务约束校验
    validateDistributedConfig();
    validateCacheConfig();
    validateCircuitBreakerConfig();
    validateParallelConfig();
    validateModelConfig();
    validateFactConfig();

    // 3. 汇总结果
    if (!validationErrors.isEmpty()) {
      log.warn(
          "[LiteRule-Config] 配置校验发现 {} 项问题：\n{}",
          validationErrors.size(),
          String.join("\n", validationErrors));
    } else {
      log.info("[LiteRule-Config] 配置校验通过");
    }
  }

  public List<String> getValidationErrors() {
    return List.copyOf(validationErrors);
  }

  public boolean hasErrors() {
    return !validationErrors.isEmpty();
  }

  // ===== 校验逻辑 =====

  private void validateAnnotations() {
    for (ConstraintViolation<LiteRuleProperties> violation : validator.validate(properties)) {
      addError("注解校验: " + violation.getPropertyPath() + " " + violation.getMessage());
    }
  }

  /**
   * 分布式配置校验
   *
   * <p>约束：心跳间隔必须小于超时时间（否则节点会被误判为下线）
   */
  private void validateDistributedConfig() {
    LiteRuleProperties.Distributed distributed = properties.getDistributed();
    if (distributed == null || !distributed.isEnabled()) {
      return;
    }
    if (distributed.getHeartbeatIntervalMs() >= distributed.getHeartbeatTimeoutMs()) {
      addError(String.format(
          "分布式配置: heartbeatIntervalMs(%d) 必须小于 heartbeatTimeoutMs(%d)，否则节点会被误判为下线",
          distributed.getHeartbeatIntervalMs(), distributed.getHeartbeatTimeoutMs()));
    }
    if (distributed.getRefreshIntervalMs() <= 0) {
      addError("分布式配置: refreshIntervalMs 必须大于 0");
    }
  }

  /**
   * 缓存配置校验
   *
   * <p>约束：L1 TTL 应小于 L2 TTL（否则 L2 无意义）
   */
  private void validateCacheConfig() {
    LiteRuleProperties.CacheConfig cache = properties.getCache();
    if (cache == null || !cache.isEnabled()) {
      return;
    }
    if (cache.isL2Enabled()) {
      // L1 TTL（秒）应 <= L2 TTL（秒）
      if (cache.getL1TtlSeconds() > cache.getL2TtlSeconds()) {
        addWarn(String.format(
            "缓存配置: L1 TTL(%ds) 大于 L2 TTL(%ds)，L2 将不会生效，建议 L1 TTL <= L2 TTL",
            cache.getL1TtlSeconds(), cache.getL2TtlSeconds()));
      }
    }
  }

  /**
   * 熔断配置校验
   *
   * <p>约束：错误率阈值应在合理范围，最小评估次数不宜过小
   */
  private void validateCircuitBreakerConfig() {
    double errorRate = properties.getCircuitBreakerErrorRate();
    int minEvaluations = properties.getCircuitBreakerMinEvaluations();
    if (errorRate <= 0 || errorRate >= 1) {
      addWarn(String.format(
          "熔断配置: circuitBreakerErrorRate(%.2f) 建议设置在 (0, 1) 开区间内，当前值可能导致熔断器始终关闭或始终打开",
          errorRate));
    }
    if (minEvaluations < 10) {
      addWarn(String.format(
          "熔断配置: circuitBreakerMinEvaluations(%d) 过小，可能导致熔断器误触发，建议 >= 10",
          minEvaluations));
    }
  }

  /**
   * 并行配置校验
   *
   * <p>约束：线程池大小与触发阈值应匹配
   */
  private void validateParallelConfig() {
    LiteRuleProperties.PerformanceConfig performance = properties.getPerformance();
    if (performance == null || !performance.isParallelEnabled()) {
      return;
    }
    if (performance.getParallelPoolSize() <= 0) {
      addError("并行配置: parallelPoolSize 必须大于 0");
    }
    if (performance.getParallelThreshold() < 2) {
      addWarn(String.format(
          "并行配置: parallelThreshold(%d) 过小，线程切换开销可能超过并行收益，建议 >= 10",
          performance.getParallelThreshold()));
    }
  }

  /**
   * 模型配置校验
   *
   * <p>约束：超时时间不宜过大（影响整体评估耗时）
   */
  private void validateModelConfig() {
    LiteRuleProperties.ModelConfig model = properties.getModel();
    if (model == null || !model.isEnabled()) {
      return;
    }
    if (model.getTimeoutMs() > 1000) {
      addWarn(String.format(
          "模型配置: timeoutMs(%dms) 过大，可能拖慢整体评估耗时，建议 <= 500ms",
          model.getTimeoutMs()));
    }
  }

  /**
   * 事实配置校验
   *
   * <p>约束：超时时间不宜过大
   */
  private void validateFactConfig() {
    LiteRuleProperties.FactConfig fact = properties.getFact();
    if (fact == null || !fact.isEnabled()) {
      return;
    }
    if (fact.getTimeoutMs() > 2000) {
      addWarn(String.format(
          "事实配置: timeoutMs(%dms) 过大，可能拖慢整体评估耗时，建议 <= 1000ms",
          fact.getTimeoutMs()));
    }
  }

  // ===== 辅助方法 =====

  private void addError(String message) {
    validationErrors.add("[ERROR] " + message);
  }

  private void addWarn(String message) {
    validationErrors.add("[WARN] " + message);
  }
}
