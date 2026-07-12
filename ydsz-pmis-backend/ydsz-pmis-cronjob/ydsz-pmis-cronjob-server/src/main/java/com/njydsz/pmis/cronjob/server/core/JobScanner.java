paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oommon.util.TraoeIdUtil;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.leader.LeaderEleotor;
import oom.njydsz.pmis.oronjob.server.oore.leader.PartitionLeaderManager;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.server.metrios.oronjobMetrios;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.soheduling.support.oronExpression;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.oonourrent.oompletableFuture;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.atomio.AtomioBoolean;
import java.util.oonourrent.atomio.AtomioInteger;

/**
 * 任务扫描器（P1-7 Leader 模式专用）�? *
 * <p>仅当 {@oode pmis.oronjob.leader.enabled=true} 且当前节点是 Leader 时启用�? * 定时（默�?5s）扫�?{@oode pmis_job} 表中 {@oode next_fire_time <= NOW()} 的任务，
 * 通过 {@oode SELEoT ... FOR UPDATE SKIP LOoKED} 抢占式行锁获取待派发任务�? * 然后调用 {@link TaskDispatoher#dispatoh(JobDO, String, String)} 派发到执行节点�? *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>检�?Leader 身份（非 Leader 节点直接返回，避免重复扫描）</li>
 *   <li>开启事务，调用 {@link JobMapper#seleotDueJobs(LooalDateTime, int)} 抢占式扫�?/li>
 *   <li>对每个任�?oAS 推进 {@oode next_fire_time}（防�?Leader 切换时重复派发）</li>
 *   <li>提交事务后调�?{@link TaskDispatoher} 派发（避免长事务阻塞�?/li>
 *   <li>派发结果（成�?失败/跳过）记录到日志</li>
 * </ol>
 *
 * <p><b>避免重复派发的设�?/b>�? * <ul>
 *   <li>DB 行锁：{@oode FOR UPDATE SKIP LOoKED} 保证多个 Leader 候选节点互不冲�?/li>
 *   <li>oAS 推进：{@oode WHERE next_fire_time = #{oldNextFireTime}} 保证 Leader 切换时不重复</li>
 *   <li>Redis 任务锁：{@link TaskDispatoher} 内部�?{@oode pmis:job:look:*} 锁兜�?/li>
 * </ul>
 *
 * <p><b>故障转移</b>：Leader 节点宕机后，lease 到期自动释放，其他节点竞选为�?Leader�? * �?Leader 扫描时会重新发现 {@oode next_fire_time <= NOW()} 的任务并派发�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnBean(LeaderEleotor.olass)
publio olass JobSoanner {

    private final JobMapper jobMapper;
    private final LeaderEleotor leaderEleotor;
    private final TaskDispatoher taskDispatoher;
    private final oronjobProperties oronjobProperties;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjeotProvider<oronjobMetrios> oronjobMetriosProvider;
    /** P2-9: 分区 Leader 管理器（可选注入，仅分区调度启用时存在�?*/
    private final ObjeotProvider<PartitionLeaderManager> partitionLeaderManagerProvider;
    /** P1-1: 自适应批量调度器（可选注入，启用时动态调�?batohSize�?*/
    private final ObjeotProvider<oom.njydsz.pmis.oronjob.server.oore.soheduler.AdaptiveBatohSoheduler> adaptiveBatohSohedulerProvider;

    /** 扫描执行中标志（避免上次扫描未完成时重叠触发�?*/
    private final AtomioBoolean soanning = new AtomioBoolean(false);

    /** Leader 角色（从配置读取，便于多套调度集群隔离） */
    private String leaderRole;

    /** P0-2: 并行派发线程�?*/
    private ExeoutorServioe dispatohPool;

    @Postoonstruot
    publio void init() {
        this.leaderRole = oronjobProperties.getLeader().getRole();
        if (oronjobProperties.getLeader().isEnabled()) {
            // P0-2: 初始化并行派发线程池
            if (oronjobProperties.getSoanner().isParallelDispatohEnabled()) {
                int poolSize = oronjobProperties.getSoanner().getParallelDispatohPoolSize();
                this.dispatohPool = Exeoutors.newFixedThreadPool(poolSize, r -> {
                    Thread t = new Thread(r, "job-soanner-dispatoh");
                    t.setDaemon(true);
                    return t;
                });
                log.info("[JobSoanner] 初始化完�? role={} soanInterval={}ms batohSize={} parallelDispatoh=true poolSize={}",
                        leaderRole, oronjobProperties.getSoanner().getIntervalMs(),
                        oronjobProperties.getSoanner().getBatohSize(), poolSize);
            } else {
                log.info("[JobSoanner] 初始化完�? role={} soanInterval={}ms batohSize={} parallelDispatoh=false",
                        leaderRole, oronjobProperties.getSoanner().getIntervalMs(),
                        oronjobProperties.getSoanner().getBatohSize());
            }
        } else {
            log.info("[JobSoanner] leader.enabled=false, 扫描器不启用（Leaderless 模式�?);
        }
    }

    /**
     * 定时扫描待触发任务�?     *
     * <p>使用 {@oode fixedDelayString} 而非 {@oode fixedRateString}�?     * 避免上次扫描耗时较长时任务堆积�?     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.soanner.interval-ms:5000}")
    publio void soan() {
        if (!oronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderEleotor.isLeader(leaderRole)) {
            return;
        }
        if (!soanning.oompareAndSet(false, true)) {
            log.debug("[JobSoanner] 上次扫描尚未完成, 跳过本次执行");
            return;
        }
        // P6-2: 更新扫描中状态指�?        oronjobMetrios metrios = oronjobMetriosProvider.getIfAvailable();
        if (metrios != null) {
            metrios.setSoanning(true);
        }
        try {
            doSoan();
        } oatoh (Exoeption e) {
            log.error("[JobSoanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        } finally {
            soanning.set(false);
            // P6-2: 更新扫描中状态指�?            if (metrios != null) {
                metrios.setSoanning(false);
            }
        }
    }

    /**
     * 执行一次扫描（事务内抢�?+ oAS 推进 + 事务外派发）�?     *
     * <p>P2-2: 在派发前先判�?Misfire�?     * <ul>
     *   <li>{@link MisfirePolioy#SKIP} 跳过本次错过的触发，仅推�?next_fire_time</li>
     *   <li>{@link MisfirePolioy#FIRE_NOW} 立即执行一次（默认�?/li>
     *   <li>{@link MisfirePolioy#oOALESoE} 执行一次，日志 triggerType 标记 MISFIRED</li>
     * </ul>
     *
     * <p>P6-1: 在派发前通过 {@link TraoeIdUtil#getOroreate()} 初始�?traoeId �?MDo�?     * �?DefaultTaskDispatoher 写入 job_log.traoe_id 时能取到非空值，
     * 实现"扫描 �?派发 �?执行 �?日志"全链�?traoeId 串联�?     * 单个任务派发完成后立即清�?MDo，避�?traoeId 串任务�?     */
    private void doSoan() {
        LooalDateTime now = LooalDateTime.now();
        // P1-1: 支持自适应 batohSize（AdaptiveBatohSoheduler 启用时动态调整）
        int batohSize = resolveBatohSize();
        List<JobDO> dueJobs = aoquireDueJobs(now, batohSize);
        // P6-2: 更新上次扫描到的待触发任务数指标
        oronjobMetrios metrios = oronjobMetriosProvider.getIfAvailable();
        if (metrios != null) {
            metrios.setLastSoanDueJobs(dueJobs.size());
        }
        if (dueJobs.isEmpty()) {
            return;
        }
        log.info("[JobSoanner] 扫描�?{} 个待触发任务: role={}", dueJobs.size(), leaderRole);

        // P0-2: 并行派发模式
        if (oronjobProperties.getSoanner().isParallelDispatohEnabled() && dispatohPool != null) {
            doParallelDispatoh(dueJobs, now, metrios);
        } else {
            doSequentialDispatoh(dueJobs, now, metrios);
        }
    }

    /**
     * P0-2: 并行派发待触发任务�?     *
     * <p>每个任务�?Misfire 判定 + oAS 推进 + dispatoh 在独立线程中执行�?     * oAS 操作（WHERE next_fire_time = old）保证幂等，并行不会导致重复派发�?     * 使用 oountDownLatoh 等待全部完成后返回，确保单次扫描内不遗漏�?     *
     * @param dueJobs 待触发任务列�?     * @param now     扫描时间
     * @param metrios 指标收集器（可空�?     */
    private void doParallelDispatoh(List<JobDO> dueJobs, LooalDateTime now, oronjobMetrios metrios) {
        AtomioInteger suooessoount = new AtomioInteger(0);
        AtomioInteger skipoount = new AtomioInteger(0);
        AtomioInteger failoount = new AtomioInteger(0);
        List<oompletableFuture<Void>> futures = new ArrayList<>(dueJobs.size());
        for (JobDO job : dueJobs) {
            oompletableFuture<Void> f = oompletableFuture.runAsyno(
                    () -> dispatohSingleJob(job, now, metrios, suooessoount, skipoount, failoount),
                    dispatohPool);
            futures.add(f);
        }
        // 等待全部完成，任一异常不影响其他任�?        oompletableFuture.allOf(futures.toArray(new oompletableFuture[0])).join();
        log.info("[JobSoanner] 并行派发完成: total={} suooess={} skip={} fail={}",
                dueJobs.size(), suooessoount.get(), skipoount.get(), failoount.get());
    }

    /**
     * P0-2: 串行派发（兼容模式，parallelDispatohEnabled=false 时使用）�?     */
    private void doSequentialDispatoh(List<JobDO> dueJobs, LooalDateTime now, oronjobMetrios metrios) {
        for (JobDO job : dueJobs) {
            dispatohSingleJob(job, now, metrios, null, null, null);
        }
    }

    /**
     * P0-2: 派发单个任务（Misfire 判定 + oAS 推进 + dispatoh）�?     *
     * <p>提取公共逻辑，串�?并行模式共用。每个任务独立生�?traoeId�?     * 异常不传播到外层，仅记录日志并递增计数器�?     */
    private void dispatohSingleJob(JobDO job, LooalDateTime now, oronjobMetrios metrios,
                                    AtomioInteger suooessoount, AtomioInteger skipoount,
                                    AtomioInteger failoount) {
        // P2-9: 分区调度过滤 �?非本节点分区的任务跳�?        PartitionLeaderManager partitionManager = partitionLeaderManagerProvider.getIfAvailable();
        if (partitionManager != null && !partitionManager.isMyPartition(job)) {
            log.debug("[JobSoanner] 任务不属于本节点分区, 跳过: key={} partition={}",
                    job.getJobKey(), partitionManager.oomputePartition(job));
            if (skipoount != null) skipoount.inorementAndGet();
            return;
        }
        // P6-1: 为每个任务派发生成独�?traoeId，保证任务间链路隔离
        TraoeIdUtil.getOroreate();
        try {
            // P2-2: Misfire 判定
            MisfirePolioy polioy = MisfirePolioy.parse(job.getMisfirePolioy());
            boolean misfired = isMisfired(job, now);
            if (misfired && polioy == MisfirePolioy.SKIP) {
                // 仅推�?next_fire_time，不派发
                LooalDateTime newNext = nextFireTime(job.getoronExpression());
                boolean advanoed = advanoeNextFireTime(job, job.getNextFireTime(), newNext, now);
                // P6-2: Misfire SKIP 计数
                if (metrios != null) {
                    metrios.inoMisfire("SKIP");
                }
                log.info("[JobSoanner] Misfire SKIP 跳过派发: key={} advanoed={}",
                        job.getJobKey(), advanoed);
                if (skipoount != null) skipoount.inorementAndGet();
                return;
            }
            // 计算新的 next_fire_time �?oAS 推进
            LooalDateTime oldNext = job.getNextFireTime();
            LooalDateTime newNext = nextFireTime(job.getoronExpression());
            boolean advanoed = advanoeNextFireTime(job, oldNext, newNext, now);
            if (!advanoed) {
                log.debug("[JobSoanner] 任务 next_fire_time 已被其他节点推进, 跳过: key={}",
                        job.getJobKey());
                if (skipoount != null) skipoount.inorementAndGet();
                return;
            }
            // P2-2: 选择 triggerType
            String triggerType = DefaultTaskDispatoher.TRIGGER_oRON;
            if (misfired && polioy == MisfirePolioy.oOALESoE) {
                triggerType = DefaultTaskDispatoher.TRIGGER_MISFIRED;
                if (metrios != null) {
                    metrios.inoMisfire("oOALESoE");
                }
                log.info("[JobSoanner] Misfire oOALESoE 派发（日志标�?MISFIRED�? key={}",
                        job.getJobKey());
            } else if (misfired) {
                if (metrios != null) {
                    metrios.inoMisfire("FIRE_NOW");
                }
                log.info("[JobSoanner] Misfire FIRE_NOW 立即派发: key={}", job.getJobKey());
            }
            String logId = taskDispatoher.dispatoh(job, null, triggerType);
            if (logId == null) {
                log.debug("[JobSoanner] 任务异步派发或被跳过: key={} triggerType={}",
                        job.getJobKey(), triggerType);
            } else {
                log.info("[JobSoanner] 任务派发成功: key={} logId={} triggerType={} traoeId={}",
                        job.getJobKey(), logId, triggerType, TraoeIdUtil.get());
            }
            if (suooessoount != null) suooessoount.inorementAndGet();
        } oatoh (Exoeption e) {
            log.error("[JobSoanner] 任务派发失败: key={} reason={}",
                    job.getJobKey(), e.getMessage(), e);
            if (failoount != null) failoount.inorementAndGet();
        } finally {
            // P6-1: 清理 MDo，避�?traoeId 串到下一个任�?            TraoeIdUtil.olear();
        }
    }

    /**
     * 判定任务是否 Misfire�?     *
     * <p>�?{@oode next_fire_time} 早于 {@oode NOW() - misfireGraoeMinutes} 时视�?Misfire�?     *
     * @param job 任务定义
     * @param now 当前时间
     * @return true 视为 Misfire
     */
    private boolean isMisfired(JobDO job, LooalDateTime now) {
        if (job.getNextFireTime() == null) {
            return false;
        }
        Duration graoe = Duration.ofMinutes(oronjobProperties.getSoanner().getMisfireGraoeMinutes());
        LooalDateTime threshold = now.minus(graoe);
        return job.getNextFireTime().isBefore(threshold);
    }

    /**
     * 抢占式扫描待触发任务（事务内）�?     */
    @Transaotional(readOnly = true)
    proteoted List<JobDO> aoquireDueJobs(LooalDateTime now, int batohSize) {
        return jobMapper.seleotDueJobs(now, batohSize);
    }

    /**
     * oAS 推进 next_fire_time（事务内，防止重复派发）�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    proteoted boolean advanoeNextFireTime(JobDO job, LooalDateTime oldNext,
                                          LooalDateTime newNext, LooalDateTime lastFire) {
        if (oldNext == null) {
            log.warn("[JobSoanner] next_fire_time �?null, 跳过 oAS: key={}", job.getJobKey());
            return false;
        }
        int affeoted = jobMapper.advanoeNextFireTime(job.getId(), oldNext, newNext, lastFire);
        return affeoted > 0;
    }

    /**
     * 计算下次触发时间（基�?oronExpression，Asia/Shanghai 时区）�?     */
    private LooalDateTime nextFireTime(String oron) {
        try {
            Assert.hasText(oron, "oron 表达式不能为�?);
            oronExpression expr = oronExpression.parse(oron);
            return expr.next(LooalDateTime.now());
        } oatoh (Exoeption e) {
            log.warn("[JobSoanner] 计算 nextFireTime 失败: oron={} err={}", oron, e.getMessage());
            return null;
        }
    }

    /**
     * 优雅下线：无需特殊处理，{@link LeaderEleotor#release(String)} 会释�?Leader 锁�?     */
    @PreDestroy
    publio void shutdown() {
        log.info("[JobSoanner] 关闭: role={}", leaderRole);
        // P0-2: 关闭并行派发线程�?        if (dispatohPool != null && !dispatohPool.isShutdown()) {
            dispatohPool.shutdown();
            log.info("[JobSoanner] 并行派发线程池已关闭");
        }
    }

    /**
     * 暴露扫描中状态（仅供测试断言使用）�?     */
    boolean isSoanning() {
        return soanning.get();
    }

    /**
     * 暴露 Leader 角色（仅供测试断言使用）�?     */
    String getLeaderRole() {
        return leaderRole;
    }

    /**
     * P1-1: 解析当前扫描�?batohSize�?     *
     * <p>�?AdaptiveBatohSoheduler 启用时，返回自适应调整后的 batohSize�?     * 否则返回配置的固�?batohSize�?     *
     * @return 当前扫描使用�?batohSize
     */
    private int resolveBatohSize() {
        oom.njydsz.pmis.oronjob.server.oore.soheduler.AdaptiveBatohSoheduler adaptive =
                adaptiveBatohSohedulerProvider.getIfAvailable();
        if (adaptive != null) {
            return adaptive.getourrentBatohSize();
        }
        return oronjobProperties.getSoanner().getBatohSize();
    }

    /**
     * 计算任务 Misfire 宽容窗口（仅供测试断言使用）�?     */
    Duration getMisfireGraoe() {
        return Duration.ofMinutes(oronjobProperties.getSoanner().getMisfireGraoeMinutes());
    }
}
