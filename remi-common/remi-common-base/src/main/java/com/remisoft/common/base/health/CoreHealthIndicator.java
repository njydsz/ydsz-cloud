package com.remisoft.common.base.health;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.core.config.MessageResolverHolder;
import com.remisoft.common.core.constant.PageConstants;

/**
 * 核心健康指标（CoreHealthIndicator）
 *
 * <p>报告核心模块的运行状态（JVM 进程级 + Core 运行时状态），
 * 供 Liveness/Readiness 探针与监控大盘使用：
 * <ul>
 *   <li><b>pid / uptime / startedAt</b> — JVM 进程信息</li>
 *   <li><b>activeThreads / threadPeak</b> — 线程池健康信号</li>
 *   <li><b>i18nResolverRegistered</b> — 国际化解析器是否已注册</li>
 *   <li><b>pageConstantsInitialized</b> — 分页配置运行时状态</li>
 *   <li><b>moduleVersion</b> — 模块版本号</li>
 * </ul>
 *
 * <p><b>状态说明：</b></p>
 * <ul>
 *   <li>{@code UP} — 国际化解析器已注册、分页配置已注入运行时值</li>
 *   <li>{@code UNKNOWN} — 分页配置未由 CoreAutoConfiguration 注入（回退编译期默认值）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class CoreHealthIndicator implements HealthIndicator {

    /** JVM 启动时间（进程级，构造时初始化） */
    private final Instant jvmStartTime;

    /** 模块版本号（构造时一次性读取，避免重复反射） */
    private final String moduleVersion;

    /**
     * 构造核心健康指标
     */
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

        // ========== Core 运行时状态（v2.1.0 新增） ==========
        details.put("moduleVersion", moduleVersion);
        details.put("i18nResolverRegistered", MessageResolverHolder.isResolverRegistered());
        details.put("pageConstantsInitialized", PageConstants.isInitialized());

        // ========== 分页配置状态 ==========
        details.put("maxPageSize", PageConstants.getMaxPageSize());
        details.put("defaultPageSize", PageConstants.getDefaultPageSize());

        // 检测 PageConstants 是否已由 CoreAutoConfiguration 注入运行时配置
        if (!PageConstants.isInitialized()) {
            return Health.unknown()
                    .withDetails(details)
                    .withDetail("warning", "PageConstants not initialized by CoreAutoConfiguration; "
                            + "falling back to compile-time defaults. "
                            + "Check remi.core.enabled or context loader configuration.")
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .build();
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
