package com.remisoft.common.base.health;

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
 * <p>报告 JVM 进程级核心健康信息，供 Liveness/Readiness 探针与监控大盘使用：
 * <ul>
 *   <li><b>uptime</b> — 进程运行时长（含 JVM 启动时间）</li>
 *   <li><b>startedAt</b> — 进程启动时间</li>
 *   <li><b>activeThreads</b> — 当前活跃线程数（Thread 峰值用于排障）</li>
 *   <li><b>threadPeak</b> — 历史峰值线程数</li>
 *   <li><b>pid</b> — 进程 PID</li>
 * </ul>
 *
 * <p>本指标始终返回 {@code UP}（JVM 存活即视为核心健康），
 * 具体业务依赖（DB/Redis/MQ）由各自 HealthIndicator 负责上报。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class CoreHealthIndicator implements HealthIndicator {

    /** JVM 启动时间（进程级，构造时初始化） */
    private final Instant jvmStartTime;

    /**
     * 构造核心健康指标
     */
    public CoreHealthIndicator() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        this.jvmStartTime = Instant.ofEpochMilli(runtime.getStartTime());
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // 进程基本信息
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        details.put("pid", runtime.getPid());
        details.put("startedAt", jvmStartTime.toString());
        details.put("uptimeSeconds", Duration.between(jvmStartTime, Instant.now()).getSeconds());

        // 线程信息（快速排障：线程暴涨通常是线程池泄漏/死锁信号）
        java.lang.management.ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        details.put("activeThreads", threadBean.getThreadCount());
        details.put("threadPeak", threadBean.getPeakThreadCount());
        details.put("daemonThreads", threadBean.getDaemonThreadCount());

        // JVM 存活即视为核心健康
        return Health.up().withDetails(details).build();
    }
}
