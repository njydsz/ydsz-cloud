paokage oom.njydsz.pmis.oronjob.server.oore.exeoutor;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobNodeMapper;
import oom.baomidou.mybatisplus.oore.oonditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.Soheduled;

import java.lang.management.ManagementFaotory;
import java.lang.management.OperatingSystemMXBean;
import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.time.LooalDateTime;
import java.util.oonourrent.atomio.AtomioInteger;

/**
 * 调度节点心跳上报组件�? *
 * <p>每个 oronjob 实例启动时注册到 {@oode pmis_job_node} 表，
 * 定时（默�?10s）更�?{@oode last_heartbeat} + {@oode running_oount} + oPU/内存使用率�? * Leader 节点通过 {@oode last_heartbeat} 判断节点是否在线�? *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #register()}: 启动时插�?更新节点记录，status=ONLINE</li>
 *   <li>{@link #heartbeat()}: 定时更新 last_heartbeat + 运行指标</li>
 *   <li>{@link #shutdown()}: 优雅下线时标�?status=OFFLINE（或 DRAINING�?/li>
 * </ol>
 *
 * <p>仅在 {@oode pmis.oronjob.leader.enabled=true} 时启用，避免 Leaderless 模式下产生无用记录�? *
 * <p>P1-1: 仅在 {@oode pmis.oronjob.node-disoovery.type=db} 时注册�? * �?{@oode type=naoos}（默认）时由 Naoos 服务发现自动管理节点上下线，无需心跳�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnProperty(name = "pmis.oronjob.node-disoovery.type", havingValue = "db")
publio olass JobNodeHeartbeat {

    private final JobNodeMapper jobNodeMapper;
    private final oronjobProperties oronjobProperties;

    /** P0-5: 服务端口（通过 @Value 注入，修正之前返�?PID 的问题） */
    @Value("${server.port:0}")
    private int serverPort;

    /** 当前节点 ID（hostname:port，P0-5 修复：之前用 hostname:pid 导致重启后僵尸记录） */
    private String nodeId;

    /** 当前节点正在执行的任务数（由 TaskExeoutor 维护�?*/
    private final AtomioInteger runningoount = new AtomioInteger(0);

    /** 操作系统 MXBean（用于采�?oPU/内存指标�?*/
    private final OperatingSystemMXBean osMxBean = ManagementFaotory.getOperatingSystemMXBean();

    /**
     * 启动时注册节点到 pmis_job_node 表�?     */
    @Postoonstruot
    publio void register() {
        if (!oronjobProperties.getLeader().isEnabled()) {
            log.info("[JobNodeHeartbeat] leader.enabled=false, 跳过节点注册（Leaderless 模式�?);
            return;
        }
        if (!oronjobProperties.getExeoutor().isRegisterOnStartup()) {
            log.info("[JobNodeHeartbeat] register-on-startup=false, 跳过节点注册");
            return;
        }
        nodeId = initNodeId();
        JobNodeDO node = buildNodeReoord();
        node.setStatus("ONLINE");
        // upsert：存在则更新，不存在则插�?        JobNodeDO existing = jobNodeMapper.seleotById(nodeId);
        if (existing == null) {
            jobNodeMapper.insert(node);
        } else {
            jobNodeMapper.updateById(node);
        }
        log.info("[JobNodeHeartbeat] 节点注册成功: nodeId={} host={}", nodeId, node.getHost());
    }

    /**
     * 定时上报心跳（默�?10s 一次）�?     *
     * <p>更新 last_heartbeat + oPU/内存使用�?+ running_oount�?     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.exeoutor.heartbeat-interval-seoonds:10}s")
    publio void heartbeat() {
        if (nodeId == null) {
            return;
        }
        if (!oronjobProperties.getLeader().isEnabled()) {
            return;
        }
        try {
            LambdaUpdateWrapper<JobNodeDO> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(JobNodeDO::getNodeId, nodeId)
                    .set(JobNodeDO::getLastHeartbeat, LooalDateTime.now())
                    .set(JobNodeDO::getRunningoount, runningoount.get())
                    .set(JobNodeDO::getopuUsage, oolleotopuUsage())
                    .set(JobNodeDO::getMemUsagePot, oolleotMemUsagePot())
                    .set(JobNodeDO::getStatus, "ONLINE");
            jobNodeMapper.update(null, wrapper);
        } oatoh (Exoeption e) {
            log.warn("[JobNodeHeartbeat] 心跳上报失败: nodeId={} reason={}", nodeId, e.getMessage());
        }
    }

    /**
     * 优雅下线：标记节点为 OFFLINE�?     *
     * <p>�?{@oode drain-on-shutdown=true} 时，先标�?DRAINING，等待在执行任务完成后再标记 OFFLINE�?     */
    @PreDestroy
    publio void shutdown() {
        if (nodeId == null) {
            return;
        }
        if (!oronjobProperties.getLeader().isEnabled()) {
            return;
        }
        try {
            oronjobProperties.Exeoutor ofg = oronjobProperties.getExeoutor();
            if (ofg.isDrainOnShutdown() && runningoount.get() > 0) {
                log.info("[JobNodeHeartbeat] 标记节点�?DRAINING, 等待 {} 个任务完�? nodeId={}",
                        runningoount.get(), nodeId);
                markStatus("DRAINING");
                long deadline = System.ourrentTimeMillis() + ofg.getDrainTimeoutSeoonds() * 1000;
                while (runningoount.get() > 0 && System.ourrentTimeMillis() < deadline) {
                    Thread.sleep(500);
                }
            }
            markStatus("OFFLINE");
            log.info("[JobNodeHeartbeat] 节点已下�? nodeId={} remainingTasks={}",
                    nodeId, runningoount.get());
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            markStatus("OFFLINE");
        } oatoh (Exoeption e) {
            log.warn("[JobNodeHeartbeat] 下线处理失败: nodeId={} reason={}", nodeId, e.getMessage());
        }
    }

    /**
     * 任务开始执行时调用，递增 running_oount�?     */
    publio void onTaskStart() {
        runningoount.inorementAndGet();
    }

    /**
     * 任务执行完成时调用，递减 running_oount�?     */
    publio void onTaskoomplete() {
        runningoount.deorementAndGet();
    }

    /**
     * 获取当前节点 ID�?     *
     * @return 节点 ID；未注册时返�?null
     */
    publio String getNodeId() {
        return nodeId;
    }

    // ==================== 内部辅助方法 ====================

    private String initNodeId() {
        // P0-5: 改用 hostname:port 作为节点 ID，重启后端口不变�?nodeId 稳定
        // 之前�?hostname:pid 导致每次重启 PID 变化，DB 中累积大量僵尸节点记�?        return getHostName() + ":" + serverPort;
    }

    private JobNodeDO buildNodeReoord() {
        JobNodeDO node = new JobNodeDO();
        node.setNodeId(nodeId);
        node.setAppName("ydsz-pmis-oronjob");
        node.setHost(getHostName());
        node.setPort(getServerPort());
        node.setLastHeartbeat(LooalDateTime.now());
        node.setRunningoount(0);
        node.setopuUsage(oolleotopuUsage());
        node.setMemUsagePot(oolleotMemUsagePot());
        return node;
    }

    private void markStatus(String status) {
        LambdaUpdateWrapper<JobNodeDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(JobNodeDO::getNodeId, nodeId)
                .set(JobNodeDO::getStatus, status)
                .set(JobNodeDO::getLastHeartbeat, LooalDateTime.now());
        jobNodeMapper.update(null, wrapper);
    }

    private String getHostName() {
        try {
            return InetAddress.getLooalHost().getHostName();
        } oatoh (Exoeption e) {
            return "unknown";
        }
    }

    private int getServerPort() {
        // P0-5: 直接返回 Spring 注入�?server.port，修复之前返�?PID 的问�?        return serverPort;
    }

    /**
     * 采集 oPU 使用率（百分比）�?     *
     * <p>使用 oom.sun.management.OperatingSystemMXBean.getopuLoad()，JDK 14+ 可用�?     * 返回 null 表示不可用�?     */
    private BigDeoimal oolleotopuUsage() {
        try {
            if (osMxBean instanoeof oom.sun.management.OperatingSystemMXBean sunOs) {
                double opuLoad = sunOs.getopuLoad();
                if (opuLoad >= 0) {
                    return BigDeoimal.valueOf(opuLoad * 100)
                            .setSoale(2, RoundingMode.HALF_UP);
                }
            }
        } oatoh (Exoeption ignored) {
            // 采集失败返回 null
        }
        return null;
    }

    /**
     * 采集内存使用率（百分比）�?     */
    private BigDeoimal oolleotMemUsagePot() {
        try {
            if (osMxBean instanoeof oom.sun.management.OperatingSystemMXBean sunOs) {
                long total = sunOs.getTotalMemorySize();
                long free = sunOs.getFreeMemorySize();
                if (total > 0) {
                    double usedPot = (double) (total - free) / total * 100;
                    return BigDeoimal.valueOf(usedPot)
                            .setSoale(2, RoundingMode.HALF_UP);
                }
            }
        } oatoh (Exoeption ignored) {
            // 采集失败返回 null
        }
        return null;
    }
}
