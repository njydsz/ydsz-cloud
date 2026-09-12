package com.njydsz.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 网关 Sentinel 自定义配置属性（{@code ydsz.sentinel.gateway.*}）。
 *
 * <p>用于绑定 YDSZ 特有的 Sentinel 网关扩展配置（Spring Cloud Alibaba 标准配置
 * 通过 {@code spring.cloud.sentinel.*} 绑定，此类仅管理 YDSZ 自定义扩展项）。
 *
 * <p>扩展配置项包括：
 *
 * <ul>
 *   <li>{@code enabled} — 是否启用 Sentinel 网关开关（false 时跳过所有 Sentinel 过滤）
 *   <li>{@code system-protection} — 系统自适应保护参数
 *   <li>{@code fallback-timeout} — 降级响应超时时间
 * </ul>
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   sentinel:
 *     gateway:
 *       enabled: true
 *       system-protection:
 *         enabled: true
 *         max-qps: 5000
 *         highest-cpu-usage: 80.0
 *       fallback-timeout: 3000
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.12
 */
@Data
@ConfigurationProperties(prefix = "ydsz.sentinel.gateway")
public class SentinelGatewayProperties {

  /** 是否启用 Sentinel 网关过滤，默认 true */
  private boolean enabled = true;

  /** 系统自适应保护参数 */
  private SystemProtection systemProtection = new SystemProtection();

  /** 降级响应超时时间（ms），默认 3000 */
  private int fallbackTimeout = 3000;

  /**
   * 系统自适应保护参数。
   *
   * <p>基于系统整体负载动态决策请求放行，提供以下维度保护：
   *
   * <ul>
   *   <li>load：系统平均负载阈值（Linux 风格 load average）
   *   <li>cpu：CPU 使用率百分比阈值
   *   <li>qps：入口总 QPS 阈值
   * </ul>
   */
  @Data
  public static class SystemProtection {
    /** 是否启用系统保护，默认 true */
    private boolean enabled = true;

    /**
     * 系统最大负载阈值（Linux 风格 load average）。
     *
     * <p>-1 表示自动检测（CPU 核心数 × 1.5），正值表示固定阈值。
     */
    private double highestSystemLoad = -1.0;

    /** 入口总 QPS 阈值（超过后拒绝所有请求），默认 5000 */
    private int maxQps = 5000;

    /** CPU 使用率阈值（百分比，超过后触发系统保护），默认 80.0 */
    private double highestCpuUsage = 80.0;
  }
}
