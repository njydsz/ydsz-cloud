package com.njydsz.pmis.cronjob.core.executor;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import com.njydsz.pmis.cronjob.mapper.JobNodeMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 调度节点心跳上报组件。
 *
 * <p>每个 cronjob 实例启动时注册到 {@code pmis_job_node} 表，
 * 定时（默认 10s）更新 {@code last_heartbeat} + {@code running_count} + CPU/内存使用率。
 * Leader 节点通过 {@code last_heartbeat} 判断节点是否在线。
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #register()}: 启动时插入/更新节点记录，status=ONLINE</li>
 *   <li>{@link #heartbeat()}: 定时更新 last_heartbeat + 运行指标</li>
 *   <li>{@link #shutdown()}: 优雅下线时标记 status=OFFLINE（或 DRAINING）</li>
 * </ol>
 *
 * <p>仅在 {@code pmis.cronjob.leader.enabled=true} 时启用，避免 Leaderless 模式下产生无用记录。
 *
 * <p>P1-1: 仅在 {@code pmis.cronjob.node-discovery.type=db} 时注册。
 * 当 {@code type=nacos}（默认）时由 Nacos 服务发现自动管理节点上下线，无需心跳。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pmis.cronjob.node-discovery.type", havingValue = "db")
public class JobNodeHeartbeat {

    private final JobNodeMapper jobNodeMapper;
    private final CronjobProperties cronjobProperties;

    /** P0-5: 服务端口（通过 @Value 注入，修正之前返回 PID 的问题） */
    @Value("${server.port:0}")
    private int serverPort;

    /** 当前节点 ID（hostname:port，P0-5 修复：之前用 hostname:pid 导致重启后僵尸记录） */
    private String nodeId;

    /** 当前节点正在执行的任务数（由 TaskExecutor 维护） */
    private final AtomicInteger runningCount = new AtomicInteger(0);

    /** 操作系统 MXBean（用于采集 CPU/内存指标） */
    private final OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();

    /**
     * 启动时注册节点到 pmis_job_node 表。
     */
    @PostConstruct
    public void register() {
        if (!cronjobProperties.getLeader().isEnabled()) {
            log.info("[JobNodeHeartbeat] leader.enabled=false, 跳过节点注册（Leaderless 模式）");
            return;
        }
        if (!cronjobProperties.getExecutor().isRegisterOnStartup()) {
            log.info("[JobNodeHeartbeat] register-on-startup=false, 跳过节点注册");
            return;
        }
        nodeId = initNodeId();
        JobNodeDO node = buildNodeRecord();
        node.setStatus("ONLINE");
        // upsert：存在则更新，不存在则插入
        JobNodeDO existing = jobNodeMapper.selectById(nodeId);
        if (existing == null) {
            jobNodeMapper.insert(node);
        } else {
            jobNodeMapper.updateById(node);
        }
        log.info("[JobNodeHeartbeat] 节点注册成功: nodeId={} host={}", nodeId, node.getHost());
    }

    /**
     * 定时上报心跳（默认 10s 一次）。
     *
     * <p>更新 last_heartbeat + CPU/内存使用率 + running_count。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.executor.heartbeat-interval-seconds:10}s")
    public void heartbeat() {
        if (nodeId == null) {
            return;
        }
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        try {
            LambdaUpdateWrapper<JobNodeDO> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(JobNodeDO::getNodeId, nodeId)
                    .set(JobNodeDO::getLastHeartbeat, LocalDateTime.now())
                    .set(JobNodeDO::getRunningCount, runningCount.get())
                    .set(JobNodeDO::getCpuUsage, collectCpuUsage())
                    .set(JobNodeDO::getMemUsagePct, collectMemUsagePct())
                    .set(JobNodeDO::getStatus, "ONLINE");
            jobNodeMapper.update(null, wrapper);
        } catch (Exception e) {
            log.warn("[JobNodeHeartbeat] 心跳上报失败: nodeId={} reason={}", nodeId, e.getMessage());
        }
    }

    /**
     * 优雅下线：标记节点为 OFFLINE。
     *
     * <p>当 {@code drain-on-shutdown=true} 时，先标记 DRAINING，等待在执行任务完成后再标记 OFFLINE。
     */
    @PreDestroy
    public void shutdown() {
        if (nodeId == null) {
            return;
        }
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        try {
            CronjobProperties.Executor cfg = cronjobProperties.getExecutor();
            if (cfg.isDrainOnShutdown() && runningCount.get() > 0) {
                log.info("[JobNodeHeartbeat] 标记节点为 DRAINING, 等待 {} 个任务完成: nodeId={}",
                        runningCount.get(), nodeId);
                markStatus("DRAINING");
                long deadline = System.currentTimeMillis() + cfg.getDrainTimeoutSeconds() * 1000;
                while (runningCount.get() > 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(500);
                }
            }
            markStatus("OFFLINE");
            log.info("[JobNodeHeartbeat] 节点已下线: nodeId={} remainingTasks={}",
                    nodeId, runningCount.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markStatus("OFFLINE");
        } catch (Exception e) {
            log.warn("[JobNodeHeartbeat] 下线处理失败: nodeId={} reason={}", nodeId, e.getMessage());
        }
    }

    /**
     * 任务开始执行时调用，递增 running_count。
     */
    public void onTaskStart() {
        runningCount.incrementAndGet();
    }

    /**
     * 任务执行完成时调用，递减 running_count。
     */
    public void onTaskComplete() {
        runningCount.decrementAndGet();
    }

    /**
     * 获取当前节点 ID。
     *
     * @return 节点 ID；未注册时返回 null
     */
    public String getNodeId() {
        return nodeId;
    }

    // ==================== 内部辅助方法 ====================

    private String initNodeId() {
        // P0-5: 改用 hostname:port 作为节点 ID，重启后端口不变则 nodeId 稳定
        // 之前用 hostname:pid 导致每次重启 PID 变化，DB 中累积大量僵尸节点记录
        return getHostName() + ":" + serverPort;
    }

    private JobNodeDO buildNodeRecord() {
        JobNodeDO node = new JobNodeDO();
        node.setNodeId(nodeId);
        node.setAppName("ydsz-pmis-cronjob");
        node.setHost(getHostName());
        node.setPort(getServerPort());
        node.setLastHeartbeat(LocalDateTime.now());
        node.setRunningCount(0);
        node.setCpuUsage(collectCpuUsage());
        node.setMemUsagePct(collectMemUsagePct());
        return node;
    }

    private void markStatus(String status) {
        LambdaUpdateWrapper<JobNodeDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(JobNodeDO::getNodeId, nodeId)
                .set(JobNodeDO::getStatus, status)
                .set(JobNodeDO::getLastHeartbeat, LocalDateTime.now());
        jobNodeMapper.update(null, wrapper);
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private int getServerPort() {
        // P0-5: 直接返回 Spring 注入的 server.port，修复之前返回 PID 的问题
        return serverPort;
    }

    /**
     * 采集 CPU 使用率（百分比）。
     *
     * <p>使用 com.sun.management.OperatingSystemMXBean.getCpuLoad()，JDK 14+ 可用。
     * 返回 null 表示不可用。
     */
    private BigDecimal collectCpuUsage() {
        try {
            if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                double cpuLoad = sunOs.getCpuLoad();
                if (cpuLoad >= 0) {
                    return BigDecimal.valueOf(cpuLoad * 100)
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception ignored) {
            // 采集失败返回 null
        }
        return null;
    }

    /**
     * 采集内存使用率（百分比）。
     */
    private BigDecimal collectMemUsagePct() {
        try {
            if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                long total = sunOs.getTotalMemorySize();
                long free = sunOs.getFreeMemorySize();
                if (total > 0) {
                    double usedPct = (double) (total - free) / total * 100;
                    return BigDecimal.valueOf(usedPct)
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
        } catch (Exception ignored) {
            // 采集失败返回 null
        }
        return null;
    }
}
