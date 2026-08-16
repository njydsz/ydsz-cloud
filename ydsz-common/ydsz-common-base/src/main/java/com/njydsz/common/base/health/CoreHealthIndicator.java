package com.njydsz.common.base.health;

import com.njydsz.common.base.i18n.MessageResolverHolder;
import com.njydsz.common.core.constant.PageConstants;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 核心健康指标（CoreHealthIndicator）
 *
 * <p>报告核心模块的运行状态（JVM 进程级 + Core 运行时状态）， 供 Liveness/Readiness 探针与监控大盘使用：
 *
 * <ul>
 *   <li><b>pid / uptime / startedAt</b> — JVM 进程信息
 *   <li><b>activeThreads / threadPeak</b> — 线程池健康信号
 *   <li><b>i18nResolverRegistered</b> — 国际化解析器是否已注册
 *   <li><b>pageConstantsInitialized</b> — 分页配置常量是否可用
 *   <li><b>moduleVersion</b> — 模块版本号
 * </ul>
 *
 * <p><b>状态说明：</b>
 *
 * <ul>
 *   <li>{@code UP} — 核心模块就绪
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CoreHealthIndicator implements HealthIndicator {

  /** JVM 启动时间（进程级，构造时初始化） */
  private final Instant jvmStartTime;

  /** 模块版本号（构造时一次性读取，避免重复反射） */
  private final String moduleVersion;

  /** 构造核心健康指标 */
  public CoreHealthIndicator() {
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    this.jvmStartTime = Instant.ofEpochMilli(runtime.getStartTime());
    this.moduleVersion = readModuleVersion();
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();

    // ========== 进程基本信息 ==========
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    details.put("pid", runtime.getPid());
    details.put("startedAt", jvmStartTime.toString());
    details.put("uptimeSeconds", Duration.between(jvmStartTime, Instant.now()).getSeconds());

    // ========== 线程信息 ==========
    java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    details.put("activeThreads", threadBean.getThreadCount());
    details.put("threadPeak", threadBean.getPeakThreadCount());
    details.put("daemonThreads", threadBean.getDaemonThreadCount());

    // ========== Core 运行时状态 ==========
    details.put("moduleVersion", moduleVersion);
    details.put("i18nResolverRegistered", MessageResolverHolder.isResolverRegistered());
    // ydsz-common-core 精简后分页配置不再支持运行时注入，直接报告编译期常量
    details.put("pageConstantsInitialized", true);

    // ========== 分页配置状态 ==========
    details.put("maxPageSize", PageConstants.MAX_PAGE_SIZE);
    details.put("defaultPageSize", PageConstants.DEFAULT_PAGE_SIZE);

    return Health.up().withDetails(details).build();
  }

  /**
   * 从 JAR 包 MANIFEST.MF 中读取 Implementation-Version
   *
   * @return 模块版本号，若无法读取返回 "unknown"
   */
  private String readModuleVersion() {
    String version = CoreHealthIndicator.class.getPackage().getImplementationVersion();
    return version != null ? version : "unknown";
  }
}
