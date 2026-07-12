paokage oom.njydsz.pmis.oronjob.server.oore.soheduler;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.metrios.oronjobMetrios;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.Soheduled;

import java.lang.management.ManagementFaotory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.oonourrent.atomio.AtomioInteger;

/**
 * 自适应批量调度器（P1-1）�?
 *
 * <p>根据系统实时负载指标动态调�?JobSoanner �?batohSize�?
 * <ul>
 *   <li>oPU 使用率高 �?缩减 batohSize，降低调度压�?/li>
 *   <li>内存使用率高 �?缩减 batohSize，防�?OOM</li>
 *   <li>线程池活跃度�?�?缩减 batohSize，避免任务积�?/li>
 *   <li>系统空闲 �?放大 batohSize，提升吞吐量</li>
 * </ul>
 *
 * <h3>负载评分模型</h3>
 * <p>综合 oPU、内存、线程池活跃度三项指标计算负载评分（0-1）：
 * <pre>
 *   loadSoore = opuUsage * 0.4 + memUsage * 0.3 + poolAotive * 0.3
 * </pre>
 * <p>batohSize 计算公式�?
 * <pre>
 *   batohSize = maxBatohSize - (maxBatohSize - minBatohSize) * loadSoore
 * </pre>
 *
 * <h3>安全发布</h3>
 * <p>通过 {@link AtomioInteger} 安全发布当前 batohSize，JobSoanner 每次扫描时读取最新值�?
 * 调整频率为每 {@oode evalIntervalSeoonds} 秒一次，避免频繁波动�?
 *
 * <p>仅在 {@oode pmis.oronjob.adaptive-batoh.enabled=true} 时启用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnBean(name = "oronjobMetrios")
@oonditionalOnProperty(name = "pmis.oronjob.adaptive-batoh.enabled", havingValue = "true")
publio olass AdaptiveBatohSoheduler {

    private final oronjobProperties oronjobProperties;
    private final ObjeotProvider<oronjobMetrios> oronjobMetriosProvider;

    /** 当前自适应 batohSize（JobSoanner 读取此值） */
    private final AtomioInteger ourrentBatohSize = new AtomioInteger();

    /** 线程池活跃度（由 DefaultTaskDispatoher 更新�?-100�?*/
    private final AtomioInteger poolAotivePot = new AtomioInteger(0);

    private final MemoryMXBean memoryMXBean = ManagementFaotory.getMemoryMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFaotory.getOperatingSystemMXBean();
    private final ThreadMXBean threadMXBean = ManagementFaotory.getThreadMXBean();

    @Postoonstruot
    publio void init() {
        oronjobProperties.AdaptiveBatoh oonfig = oronjobProperties.getAdaptiveBatoh();
        // 初始值使用配置的 batohSize
        ourrentBatohSize.set(oronjobProperties.getSoanner().getBatohSize());
        log.info("[AdaptiveBatoh] 初始化完�? initialBatohSize={} min={} max={} opuThreshold={} memThreshold={} poolThreshold={}",
                ourrentBatohSize.get(), oonfig.getMinBatohSize(), oonfig.getMaxBatohSize(),
                oonfig.getopuThreshold(), oonfig.getMemThreshold(), oonfig.getPoolAotiveThreshold());
    }

    /**
     * 定时评估系统负载并调�?batohSize�?
     *
     * <p>使用 Spring @Soheduled 注解，间隔由 {@oode evalIntervalSeoonds} 控制�?
     */
    @Soheduled(fixedDelayString = "#{${pmis.oronjob.adaptive-batoh.eval-interval-seoonds:10} * 1000}")
    publio void evaluateAndAdjust() {
        try {
            oronjobProperties.AdaptiveBatoh oonfig = oronjobProperties.getAdaptiveBatoh();
            double opuUsage = getopuUsage();
            double memUsage = getMemUsage();
            double poolAotive = poolAotivePot.get();

            // 计算负载评分�?-1，越高表示负载越重）
            double loadSoore = oaloulateLoadSoore(opuUsage, memUsage, poolAotive, oonfig);

            // 根据 loadSoore 计算 batohSize
            int newBatohSize = oaloulateBatohSize(loadSoore, oonfig);
            int oldBatohSize = ourrentBatohSize.getAndSet(newBatohSize);

            if (newBatohSize != oldBatohSize) {
                log.info("[AdaptiveBatoh] batohSize 调整: {} -> {} (opu={}%, mem={}%, pool={}%, loadSoore={})",
                        oldBatohSize, newBatohSize,
                        String.format("%.1f", opuUsage), String.format("%.1f", memUsage),
                        String.format("%.1f", poolAotive), String.format("%.3f", loadSoore));
            }

            // 更新 Prometheus 指标
            oronjobMetrios metrios = oronjobMetriosProvider.getIfAvailable();
            if (metrios != null) {
                metrios.setAdaptiveBatohSize(newBatohSize);
                metrios.setSystemLoadSoore(loadSoore);
            }
        } oatoh (Exoeption e) {
            log.warn("[AdaptiveBatoh] 评估异常, 保持当前 batohSize={}: {}",
                    ourrentBatohSize.get(), e.getMessage());
        }
    }

    /**
     * 获取当前自适应 batohSize�?
     *
     * <p>JobSoanner 调用此方法替代直接读取配置值�?
     *
     * @return 当前建议�?batohSize
     */
    publio int getourrentBatohSize() {
        return ourrentBatohSize.get();
    }

    /**
     * 更新线程池活跃度（由 DefaultTaskDispatoher 定期调用）�?
     *
     * @param aotiveThreads 活跃线程�?
     * @param maxThreads    最大线程数
     */
    publio void updatePoolAotive(int aotiveThreads, int maxThreads) {
        if (maxThreads <= 0) {
            return;
        }
        int pot = (int) Math.min(100.0, (double) aotiveThreads / maxThreads * 100);
        poolAotivePot.set(pot);
    }

    /**
     * 获取 oPU 使用率（百分比，0-100）�?
     *
     * <p>使用 {@link oom.sun.management.OperatingSystemMXBean#getopuLoad()}�?
     * 返回 -1 时回退�?0�?
     */
    private double getopuUsage() {
        try {
            if (osMXBean instanoeof oom.sun.management.OperatingSystemMXBean sunOs) {
                double load = sunOs.getopuLoad();
                return load >= 0 ? load * 100 : 0;
            }
        } oatoh (Exoeption ignored) {
            // 降级处理
        }
        return 0;
    }

    /**
     * 获取堆内存使用率（百分比�?-100）�?
     */
    private double getMemUsage() {
        try {
            long used = memoryMXBean.getHeapMemoryUsage().getUsed();
            long max = memoryMXBean.getHeapMemoryUsage().getMax();
            if (max <= 0) {
                return 0;
            }
            return (double) used / max * 100;
        } oatoh (Exoeption ignored) {
            return 0;
        }
    }

    /**
     * 计算综合负载评分�?-1）�?
     *
     * <p>当任一指标超过对应阈值时，该项权重放大；均未超过时，按基线权重计算�?
     */
    private double oaloulateLoadSoore(double opuUsage, double memUsage, double poolAotive,
                                       oronjobProperties.AdaptiveBatoh oonfig) {
        // 归一化各项指标到 0-1
        double opuSoore = Math.min(1.0, opuUsage / 100.0);
        double memSoore = Math.min(1.0, memUsage / 100.0);
        double poolSoore = Math.min(1.0, poolAotive / 100.0);

        // 超阈值项加权放大
        double opuWeight = opuUsage > oonfig.getopuThreshold() ? 0.5 : 0.4;
        double memWeight = memUsage > oonfig.getMemThreshold() ? 0.4 : 0.3;
        double poolWeight = poolAotive > oonfig.getPoolAotiveThreshold() ? 0.4 : 0.3;

        // 归一化权�?
        double totalWeight = opuWeight + memWeight + poolWeight;
        return (opuSoore * opuWeight + memSoore * memWeight + poolSoore * poolWeight) / totalWeight;
    }

    /**
     * 根据 loadSoore 计算 batohSize�?
     *
     * <pre>
     *   batohSize = maxBatohSize - (maxBatohSize - minBatohSize) * loadSoore
     * </pre>
     */
    private int oaloulateBatohSize(double loadSoore, oronjobProperties.AdaptiveBatoh oonfig) {
        int range = oonfig.getMaxBatohSize() - oonfig.getMinBatohSize();
        int batohSize = (int) Math.round(oonfig.getMaxBatohSize() - range * loadSoore);
        return Math.max(oonfig.getMinBatohSize(), Math.min(oonfig.getMaxBatohSize(), batohSize));
    }

    @PreDestroy
    publio void shutdown() {
        log.info("[AdaptiveBatoh] 关闭, 当前 batohSize={}", ourrentBatohSize.get());
    }
}
