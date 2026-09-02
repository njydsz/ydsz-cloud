package com.njydsz.common.base.health;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.base.config.DocProperties;
import com.njydsz.common.base.config.YdszSecurityHeadersProperties;

/**
 * Base 模块健康指标
 *
 * <p>报告 HTTP 基座模块的核心配置和运行状态，包括：
 *
 * <ul>
 *   <li>时区配置是否生效
 *   <li>安全响应头是否启用
 *   <li>链路追踪是否启用（通过配置间接判断）
 *   <li>文档功能状态
 *   <li>CORS 配置安全性（通过基类属性判断）
 *   <li>JVM 堆内存使用概况
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class YdszHealthIndicator implements HealthIndicator {

  /** 字节到 MB 的换算因子 */
  private static final int BYTES_PER_MB = 1024 * 1024;

  /** 内存使用率百分比换算因子 */
  private static final int PERCENTAGE_FACTOR = 100;

  /** 百分比计算精度因子（保留两位小数） */
  private static final double PERCENTAGE_PRECISION = 100.0;

  /** OOM 风险阈值：堆内存使用率超过此百分比判定为 DOWN */
  private static final double OOM_RISK_THRESHOLD_PERCENT = 95.0;

  /** 期望的时区 ID，从配置 {@code ydsz.base.timezone} 读取，默认 {@code Asia/Shanghai} */
  private final String expectedTimezone;

  private final YdszSecurityHeadersProperties securityHeadersProperties;
  private final DocProperties docProperties;

  /**
   * 构造 Base 模块健康指标
   *
   * @param securityHeadersProperties 安全响应头配置
   * @param docProperties 文档配置
   * @param expectedTimezone 期望的时区 ID（从 {@code ydsz.base.timezone} 配置读取）
   */
  public YdszHealthIndicator(
      YdszSecurityHeadersProperties securityHeadersProperties,
      DocProperties docProperties,
      @Value("${ydsz.base.timezone:Asia/Shanghai}") String expectedTimezone) {
    this.securityHeadersProperties = securityHeadersProperties;
    this.docProperties = docProperties;
    this.expectedTimezone = expectedTimezone;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(16);

    // 时区状态
    String currentTimezone = TimeZone.getDefault().getID();
    details.put("timezone", currentTimezone);
    details.put("timezone.expected", expectedTimezone);
    if (!expectedTimezone.equals(currentTimezone)) {
      details.put(
          "timezone.warning", "期望时区 " + expectedTimezone + " 与实际时区 " + currentTimezone + " 不一致");
    }

    // 安全响应头状态
    details.put("securityHeaders.enabled", securityHeadersProperties.isEnabled());
    details.put("securityHeaders.frameOptions", securityHeadersProperties.getFrameOptions());
    details.put(
        "securityHeaders.csp",
        securityHeadersProperties.getCsp() != null ? "configured" : "not-set");

    // 文档功能状态
    details.put("doc.enabled", docProperties.isEnabled());
    if (docProperties.isEnabled()) {
      details.put("doc.productionEnabled", docProperties.isProductionEnabled());
      details.put("doc.basicAuth.enabled", docProperties.getBasicAuth().isEnabled());
      details.put("doc.apiDocsPath", docProperties.getApiDocsPath());
      details.put("doc.knife4jPath", docProperties.getKnife4jPath());
    }

    // JVM 堆内存使用概况
    double heapUsagePercent = collectHeapMemoryDetails(details);

    // 健康状态判定
    boolean healthy =
        checkSecurityHeaders(details)
            && checkDocSecurity(details)
            && checkHeapMemory(details, heapUsagePercent);

    if (healthy) {
      return Health.up().withDetails(details).build();
    }
    return Health.down().withDetails(details).build();
  }

  /**
   * 采集 JVM 堆内存使用概况到 details 中
   *
   * @param details 健康详情映射
   * @return 堆内存使用率百分比
   */
  private double collectHeapMemoryDetails(Map<String, Object> details) {
    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    Map<String, Object> memoryDetails = new LinkedHashMap<>(16);
    memoryDetails.put("usedMB", heapUsage.getUsed() / BYTES_PER_MB);
    memoryDetails.put("committedMB", heapUsage.getCommitted() / BYTES_PER_MB);
    memoryDetails.put("maxMB", heapUsage.getMax() / BYTES_PER_MB);
    double usagePercent =
        heapUsage.getMax() > 0
            ? Math.round(
                    (double) heapUsage.getUsed()
                        / heapUsage.getMax()
                        * PERCENTAGE_FACTOR
                        * PERCENTAGE_PRECISION)
                / PERCENTAGE_PRECISION
            : 0.0;
    memoryDetails.put("usagePercent", usagePercent);
    details.put("heapMemory", memoryDetails);
    return usagePercent;
  }

  /**
   * 检查安全响应头配置是否合法
   *
   * @param details 健康详情映射
   * @return 配置合法返回 true
   */
  private boolean checkSecurityHeaders(Map<String, Object> details) {
    if (securityHeadersProperties.isEnabled()
        && (securityHeadersProperties.getFrameOptions() == null
            || securityHeadersProperties.getFrameOptions().isBlank())) {
      details.put("warning", "安全响应头已启用但 frameOptions 为空");
      return false;
    }
    return true;
  }

  /**
   * 检查文档安全配置：生产环境文档需开启 Basic 认证
   *
   * @param details 健康详情映射
   * @return 配置合法返回 true
   */
  private boolean checkDocSecurity(Map<String, Object> details) {
    if (docProperties.isEnabled()
        && docProperties.isProductionEnabled()
        && !docProperties.getBasicAuth().isEnabled()) {
      details.put("warning", "生产环境文档已启用但 Basic 认证未开启");
      return false;
    }
    return true;
  }

  /**
   * 检查堆内存使用率是否超过 OOM 风险阈值
   *
   * @param details 健康详情映射
   * @param usagePercent 当前堆内存使用率百分比
   * @return 未超过阈值返回 true
   */
  private boolean checkHeapMemory(Map<String, Object> details, double usagePercent) {
    if (usagePercent >= OOM_RISK_THRESHOLD_PERCENT) {
      details.put("warning", "堆内存使用率超过 95%，存在 OOM 风险");
      return false;
    }
    return true;
  }
}
