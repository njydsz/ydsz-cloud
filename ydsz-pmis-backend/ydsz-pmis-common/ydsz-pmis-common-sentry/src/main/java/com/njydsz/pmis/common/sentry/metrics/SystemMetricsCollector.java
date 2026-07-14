package com.njydsz.pmis.common.sentry.metrics;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.pmis.common.sentry.spi.MetricsCollector;

import lombok.extern.slf4j.Slf4j;

/**
 * 系统资源指标采集器
 *
 * <p>采集 CPU、内存、磁盘、网络、进程级指标。
 * 基于 JDK ManagementFactory 实现，零额外依赖。
 *
 * <p>采集指标：
 * <ul>
 *   <li>ydsz.system.cpu.usage - CPU 使用率（0.0~1.0）</li>
 *   <li>ydsz.system.cpu.load_avg - CPU 平均负载</li>
 *   <li>ydsz.system.memory.heap.used - 堆内存已用（bytes）</li>
 *   <li>ydsz.system.memory.heap.max - 堆内存最大（bytes）</li>
 *   <li>ydsz.system.memory.non_heap.used - 非堆内存已用（bytes）</li>
 *   <li>ydsz.system.disk.free - 磁盘可用空间（bytes）</li>
 *   <li>ydsz.system.disk.total - 磁盘总空间（bytes）</li>
 *   <li>ydsz.system.process.uptime - 进程运行时长（秒）</li>
 *   <li>ydsz.system.process.thread_count - 线程数</li>
 *   <li>ydsz.system.process.gc.count - GC 总次数</li>
 *   <li>ydsz.system.process.gc.time - GC 总耗时（毫秒）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class SystemMetricsCollector {

    private final MetricsCollector metricsCollector;
    private final OperatingSystemMXBean osMxBean;
    private final RuntimeMXBean runtimeMxBean;
    private final MemoryMXBean memoryMxBean;
    private final ThreadMXBean threadMxBean;
    private final GarbageCollectorMXBean[] gcMxBeans;

    /** 上次 GC 时间，用于计算增量 */
    private final AtomicLong lastGcTime = new AtomicLong(0);

    public SystemMetricsCollector(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        this.osMxBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        this.memoryMxBean = ManagementFactory.getMemoryMXBean();
        this.threadMxBean = ManagementFactory.getThreadMXBean();
        this.gcMxBeans = ManagementFactory.getGarbageCollectorMXBeans()
                .toArray(new GarbageCollectorMXBean[0]);
        log.info("[Sentry] SystemMetricsCollector 初始化完成");
    }

    /**
     * 采集并上报系统资源指标
     */
    public void collect() {
        if (metricsCollector == null) {
            return;
        }
        try {
            collectCpuMetrics();
            collectMemoryMetrics();
            collectDiskMetrics();
            collectProcessMetrics();
            collectGcMetrics();
        } catch (Exception e) {
            log.debug("[Sentry] 系统指标采集失败: {}", e.getMessage());
        }
    }

    private void collectCpuMetrics() {
        // FQN-OK: name conflict with java.lang.management.OperatingSystemMXBean
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            double cpuUsage = sunOs.getProcessCpuLoad();
            if (cpuUsage >= 0) {
                metricsCollector.setGauge("ydsz.system.cpu.usage",
                        "CPU 使用率", null, cpuUsage);
            }
            double systemCpuLoad = sunOs.getSystemCpuLoad();
            if (systemCpuLoad >= 0) {
                metricsCollector.setGauge("ydsz.system.cpu.system_usage",
                        "系统 CPU 使用率", null, systemCpuLoad);
            }
        }
        metricsCollector.setGauge("ydsz.system.cpu.load_avg",
                "CPU 平均负载", null, osMxBean.getSystemLoadAverage());
        metricsCollector.setGauge("ydsz.system.cpu.available_processors",
                "可用 CPU 核数", null, osMxBean.getAvailableProcessors());
    }

    private void collectMemoryMetrics() {
        MemoryUsage heapUsage = memoryMxBean.getHeapMemoryUsage();
        metricsCollector.setGauge("ydsz.system.memory.heap.used",
                "堆内存已用", null, heapUsage.getUsed());
        metricsCollector.setGauge("ydsz.system.memory.heap.max",
                "堆内存最大", null, heapUsage.getMax());
        metricsCollector.setGauge("ydsz.system.memory.heap.committed",
                "堆内存已分配", null, heapUsage.getCommitted());

        MemoryUsage nonHeapUsage = memoryMxBean.getNonHeapMemoryUsage();
        metricsCollector.setGauge("ydsz.system.memory.non_heap.used",
                "非堆内存已用", null, nonHeapUsage.getUsed());
        metricsCollector.setGauge("ydsz.system.memory.non_heap.committed",
                "非堆内存已分配", null, nonHeapUsage.getCommitted());
    }

    private void collectDiskMetrics() {
        File[] roots = File.listRoots();
        if (roots != null) {
            for (int i = 0; i < roots.length; i++) {
                File root = roots[i];
                metricsCollector.setGauge("ydsz.system.disk.free",
                        "磁盘可用空间", Map.of("mount", root.getAbsolutePath()),
                        root.getFreeSpace());
                metricsCollector.setGauge("ydsz.system.disk.total",
                        "磁盘总空间", Map.of("mount", root.getAbsolutePath()),
                        root.getTotalSpace());
                metricsCollector.setGauge("ydsz.system.disk.usable",
                        "磁盘可用空间（含权限）", Map.of("mount", root.getAbsolutePath()),
                        root.getUsableSpace());
            }
        }
    }

    private void collectProcessMetrics() {
        metricsCollector.setGauge("ydsz.system.process.uptime",
                "进程运行时长（秒）", null, runtimeMxBean.getUptime() / 1000.0);
        metricsCollector.setGauge("ydsz.system.process.thread_count",
                "线程数", null, threadMxBean.getThreadCount());
        metricsCollector.setGauge("ydsz.system.process.daemon_thread_count",
                "守护线程数", null, threadMxBean.getDaemonThreadCount());
        metricsCollector.setGauge("ydsz.system.process.peak_thread_count",
                "峰值线程数", null, threadMxBean.getPeakThreadCount());
    }

    private void collectGcMetrics() {
        long totalGcCount = 0;
        long totalGcTime = 0;
        for (GarbageCollectorMXBean gc : gcMxBeans) {
            totalGcCount += gc.getCollectionCount();
            totalGcTime += gc.getCollectionTime();
        }
        metricsCollector.setGauge("ydsz.system.process.gc.count",
                "GC 总次数", null, totalGcCount);
        metricsCollector.setGauge("ydsz.system.process.gc.time",
                "GC 总耗时（毫秒）", null, totalGcTime);

        long prevTime = lastGcTime.getAndSet(totalGcTime);
        long gcDelta = totalGcTime - prevTime;
        if (gcDelta > 0) {
            metricsCollector.incrementCounter("ydsz.system.process.gc.time_delta",
                    "GC 增量耗时（毫秒）", null, gcDelta);
        }
    }
}
