package com.njydsz.common.audit.health;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.audit.config.AuditProperties;
import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.core.HealthInfo;

/**
 * 审计模块健康检查指示器
 *
 * <p>通过 Spring Boot Actuator 暴露 {@code /actuator/health/audit} 端点， 用于监控审计记录器的运行状态，包括队列/缓冲区状态、丢弃计数等。
 *
 * <p>健康状态由 {@link AuditRecorder#health()} 多态分发， 无需在指示器中做 instanceof 类型判断。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class AuditHealthIndicator implements HealthIndicator {

  /** 审计记录器 */
  private final AuditRecorder auditRecorder;

  /** 审计配置属性 */
  private final AuditProperties auditProperties;

  /**
   * 构造审计健康检查指示器
   *
   * @param auditRecorder 审计记录器
   * @param auditProperties 审计配置属性
   */
  public AuditHealthIndicator(AuditRecorder auditRecorder, AuditProperties auditProperties) {
    this.auditRecorder = auditRecorder;
    this.auditProperties = auditProperties;
  }

  /**
   * 执行健康检查
   *
   * <p>通过多态调用 {@link AuditRecorder#health()} 获取健康信息， 再映射为 Spring Boot Actuator 的 {@link Health} 对象。
   *
   * @return Health 状态（UP / DOWN）及明细
   */
  @Override
  public Health health() {
    HealthInfo healthInfo = auditRecorder.health();

    Map<String, Object> details = new LinkedHashMap<>(healthInfo.getDetails());
    details.put("module", "audit");
    details.put("recorder", auditRecorder.getName());
    details.put("storageType", auditProperties.getStorageType());

    Health.Builder builder =
        healthInfo.getStatus() == HealthInfo.Status.DOWN ? Health.down() : Health.up();

    return builder.withDetails(details).build();
  }
}
