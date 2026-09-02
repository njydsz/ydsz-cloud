package com.njydsz.cronjob.server.config;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * P1-12: 配置启动校验器。
 *
 * <p>应用启动时（{@link ApplicationReadyEvent}）对所有配置类执行 JSR-380 校验，
 * 配置错误立即抛出异常，阻止应用启动，实现"启动即报错"（Fail-Fast）。
 *
 * <h3>校验范围</h3>
 *
 * <ul>
 *   <li>{@link CronjobProperties} 主配置（jobLockTtl、schedulerPoolSize 等）
 *   <li>{@link LeaderConfig} Leader 选举配置（leaseSeconds、renewIntervalSeconds 等）
 *   <li>{@link NodeConfig} 节点配置（port、heartbeatIntervalMs 等）
 *   <li>{@link NodeHealthConfig} 健康检查配置
 *   <li>{@link WebhookRetryConfig} Webhook 重试配置
 * </ul>
 *
 * <h3>设计理念</h3>
 *
 * <p>配置错误（如租约 &lt; 续期间隔、端口越界）属于"不可恢复错误"，应在启动阶段立即暴露，
 * 而非等到运行时才暴露为难以排查的故障。
 *
 * @author ydsz-team
 * @since 1.0.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CronjobConfigValidator {

  private final CronjobProperties cronjobProperties;

  /**
   * 应用就绪后执行配置校验。
   *
   * <p>若有任何配置违反约束，抛出 {@link IllegalStateException} 阻止应用继续启动。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void validateOnStartup() {
    log.info("[ConfigValidator] 开始执行配置启动校验...");

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();

    // 校验 Leader 配置
    validateBean(validator, cronjobProperties.getLeader(), "ydsz.cronjob.leader");

    // 校验 Node 配置
    validateBean(validator, cronjobProperties.getNode(), "ydsz.cronjob.node");

    // 校验 Webhook 重试配置
    validateBean(validator, cronjobProperties.getWebhookRetry(), "ydsz.cronjob.webhook-retry");

    // 校验全局配置（TTL 等）
    validateGlobalConfig();

    log.info("[ConfigValidator] 配置启动校验通过 ✓");
  }

  /**
   * 校验单个配置 Bean。
   *
   * @param validator JSR-380 Validator
   * @param bean 待校验对象
   * @param prefix 配置前缀（用于错误信息）
   */
  private void validateBean(Validator validator, Object bean, String prefix) {
    Set<ConstraintViolation<Object>> violations = validator.validate(bean);
    if (!violations.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("配置校验失败 [").append(prefix).append("]:\n");
      for (ConstraintViolation<Object> violation : violations) {
        sb.append("  - ").append(violation.getPropertyPath())
          .append(": ").append(violation.getMessage()).append("\n");
      }
      String errorMessage = sb.toString();
      log.error("[ConfigValidator] {}", errorMessage);
      throw new IllegalStateException(errorMessage);
    }
  }

  /**
   * 校验全局配置约束（需要跨字段的逻辑校验）。
   */
  private void validateGlobalConfig() {
    // jobLockTtlMin <= jobLockTtl <= jobLockTtlMax
    if (cronjobProperties.getJobLockTtlMin() != null
        && cronjobProperties.getJobLockTtlMax() != null
        && cronjobProperties.getJobLockTtlMin().compareTo(cronjobProperties.getJobLockTtlMax()) > 0) {
      String msg = "配置校验失败: ydsz.cronjob.job-lock-ttl-min 不能大于 job-lock-ttl-max";
      log.error("[ConfigValidator] {}", msg);
      throw new IllegalStateException(msg);
    }
    if (cronjobProperties.getSchedulerPoolSize() <= 0) {
      String msg = "配置校验失败: ydsz.cronjob.scheduler-pool-size 必须大于 0";
      log.error("[ConfigValidator] {}", msg);
      throw new IllegalStateException(msg);
    }
  }
}
