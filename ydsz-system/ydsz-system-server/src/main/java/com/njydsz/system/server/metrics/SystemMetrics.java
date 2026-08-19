package com.njydsz.system.server.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;

/**
 * 系统模块 Micrometer 指标采集器
 *
 * <p>{@code ydsz-system} 微服务的 Prometheus 指标出口，继承 {@link SentryMetricsAdapter} 实现指标统一管理。 通过 Spring
 * Boot Actuator 在 {@code /actuator/prometheus} 端点暴露，供 Grafana / Prometheus 抓取。
 *
 * <p><b>架构优化（P2-2）：</b>继承 {@link SentryMetricsAdapter}，统一指标前缀 {@code ydsz_system_}，
 * 仅<b>保留安全校验和数据质量指标</b>。配置/字典/变量等低频管理操作的详细读/缓存指标已移除，
 * 如需接口级监控可通过 AOP + {@code @Timed} 注解统一采集。
 *
 * <p><b>暴露指标清单：</b>
 *
 * <ul>
 *   <li>{@code ydsz_system_config_validation_warning_total} — 配置值格式校验告警
 *   <li>{@code ydsz_system_app_validate_success_total} / {@code app_validate_fail_total} — 应用密钥校验成功
 *       / 失败
 * </ul>
 *
 * <p><b>启用条件：</b>{@code @ConditionalOnClass(MeterRegistry.class)} — Micrometer 存在时启用
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SentryMetricsAdapter 通用指标基类（封装 Counter / Timer 样板代码）
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class SystemMetrics extends SentryMetricsAdapter {

  public SystemMetrics() {
    super("ydsz_system_");
  }

  /**
   * 记录配置值格式校验告警
   *
   * <p>当配置值未通过格式校验时调用（不阻止保存，仅记录告警）。可通过
   * Grafana 监控异常配置比例。
   *
   * <p>由 {@code ConfigServiceImpl.validateConfigValueFormat} 校验失败时调用。
   */
  public void recordConfigValidationWarning() {
    incrementCounter("config_validation_warning_total");
  }

  /**
   * 记录应用密钥校验成功
   *
   * <p>由 {@code AppInfoServiceImpl.validateClient} 校验通过时调用。
   */
  public void recordAppValidateSuccess() {
    incrementCounter("app_validate_success_total");
  }

  /**
   * 记录应用密钥校验失败
   *
   * <p>由 {@code AppInfoServiceImpl.validateClient} 校验失败时调用（含应用不存在 / 未启用 / 密钥不匹配）。
   * 失败率突增通常是密钥泄露 / 暴力破解的早期信号，建议接入告警。
   */
  public void recordAppValidateFail() {
    incrementCounter("app_validate_fail_total");
  }
}
