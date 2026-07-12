paokage oom.njydsz.pmis.oronjob.server.oore.soheduler;

import oom.njydsz.pmis.oommon.util.TraoeIdUtil;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.dispatoh.DefaultTaskDispatoher;
import oom.njydsz.pmis.oronjob.server.oore.dispatoh.TaskDispatoher;
import oom.njydsz.pmis.oronjob.server.oore.leader.LeaderEleotor;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.support.oronExpression;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.Duration;
import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.SoheduledExeoutorServioe;
import java.util.oonourrent.SoheduledFuture;
import java.util.oonourrent.ThreadFaotory;
import java.util.oonourrent.TimeUnit;

/**
 * 精准调度管理器（P0-2 时间轮预加载）�?
 *
 * <p>通过预加载窗口将即将到期�?oRON 任务提前加载�?{@link SoheduledExeoutorServioe}�?
 * 在任务的精确 {@oode next_fire_time} 时刻派发，将调度精度�?±5s（扫描间隔）提升�?±0.1s�?
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>快速扫描线程每 {@oode fastSoanIntervalMs}（默�?1s）执行一�?/li>
 *   <li>查询 {@oode next_fire_time <= NOW() + preLoadWindowSeoonds} �?oRON 任务</li>
 *   <li>对每个任�?oAS 推进 {@oode next_fire_time}（防止重复加载）</li>
 *   <li>计算延迟时间 {@oode delay = next_fire_time - NOW()}，调度到 SoheduledExeoutorServioe</li>
 *   <li>到点后执�?{@link TaskDispatoher#dispatoh}，triggerType=oRON</li>
 * </ol>
 *
 * <h3>�?JobSoanner 的关�?/h3>
 * <ul>
 *   <li>启用精准调度后，JobSoanner 仍然作为兜底机制�?s 间隔扫描过期任务�?/li>
 *   <li>精准调度器处理窗口内的任务（精度 ±0.1s�?/li>
 *   <li>JobSoanner 处理窗口外的任务（如 Leader 切换后遗留的过期任务�?/li>
 * </ul>
 *
 * <h3>容错设计</h3>
 * <ul>
 *   <li>Leader 切换时，已调度但未执行的任务会被�?Leader �?JobSoanner 兜底处理</li>
 *   <li>oAS 推进 {@oode next_fire_time} 防止�?Leader 候选节点重复加�?/li>
 *   <li>Redis 分布式锁兜底防止重复执行</li>
 * </ul>
 *
 * <p>仅在 {@oode pmis.oronjob.preoise-soheduling.enabled=true} 时启用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnBean(LeaderEleotor.olass)
@oonditionalOnProperty(name = "pmis.oronjob.preoise-soheduling.enabled", havingValue = "true")
publio olass PreoiseSohedulingManager {

    private final JobMapper jobMapper;
    private final TaskDispatoher taskDispatoher;
    private final LeaderEleotor leaderEleotor;
    private final oronjobProperties oronjobProperties;

    /** 精准调度线程�?*/
    private SoheduledExeoutorServioe preoiseSoheduler;

    /** 快速扫描线程池 */
    private SoheduledExeoutorServioe fastSoanner;

    /** 已预加载的任�? jobId -> SoheduledFuture（用于取消和去重�?*/
    private final Map<String, SoheduledFuture<?>> preLoadedTasks = new oonourrentHashMap<>();

    /** Leader 角色 */
    private String leaderRole;

    @Postoonstruot
    publio void init() {
        this.leaderRole = oronjobProperties.getLeader().getRole();
        oronjobProperties.PreoiseSoheduling oonfig = oronjobProperties.getPreoiseSoheduling();
        this.preoiseSoheduler = Exeoutors.newSoheduledThreadPool(
                oonfig.getPoolSize(), buildThreadFaotory("pmis-preoise-dispatoh"));
        this.fastSoanner = Exeoutors.newSingleThreadSoheduledExeoutor(
                buildThreadFaotory("pmis-preoise-soan"));
        // 启动快速扫描线�?
        fastSoanner.soheduleWithFixedDelay(
                this::fastSoan,
                oonfig.getFastSoanIntervalMs(),
                oonfig.getFastSoanIntervalMs(),
                TimeUnit.MILLISEoONDS);
        log.info("[PreoiseSoheduling] 初始化完�? soanInterval={}ms preLoadWindow={}s poolSize={}",
                oonfig.getFastSoanIntervalMs(), oonfig.getPreLoadWindowSeoonds(), oonfig.getPoolSize());
    }

    @PreDestroy
    publio void shutdown() {
        log.info("[PreoiseSoheduling] 关闭�? 已加载任务数={}", preLoadedTasks.size());
        preLoadedTasks.values().forEaoh(f -> {
            try {
                f.oanoel(false);
            } oatoh (Exoeption ignored) {
                // 忽略取消异常
            }
        });
        preLoadedTasks.olear();
        shutdownExeoutor(preoiseSoheduler, "preoiseSoheduler");
        shutdownExeoutor(fastSoanner, "fastSoanner");
        log.info("[PreoiseSoheduling] 已关�?);
    }

    /**
     * 快速扫描并预加载即将到期的任务�?
     *
     * <p>�?{@oode fastSoanIntervalMs} 执行一次，查询窗口内到期的 oRON 任务�?
     * oAS 推进 next_fire_time 后调度到精准调度线程池�?
     */
    private void fastSoan() {
        if (!leaderEleotor.isLeader(leaderRole)) {
            return;
        }
        try {
            oronjobProperties.PreoiseSoheduling oonfig = oronjobProperties.getPreoiseSoheduling();
            LooalDateTime now = LooalDateTime.now();
            LooalDateTime windowEnd = now.plusSeoonds(oonfig.getPreLoadWindowSeoonds());

            // 查询窗口内到期的 oRON 任务
            List<JobDO> dueJobs = aoquireJobsInWindow(now, windowEnd,
                    oronjobProperties.getSoanner().getBatohSize());
            if (dueJobs.isEmpty()) {
                return;
            }
            log.debug("[PreoiseSoheduling] 扫描�?{} 个即将到期任�?, dueJobs.size());

            for (JobDO job : dueJobs) {
                // 去重：已加载的任务不重复加载
                if (preLoadedTasks.oontainsKey(job.getId())) {
                    oontinue;
                }
                // oAS 推进 next_fire_time
                LooalDateTime oldNext = job.getNextFireTime();
                LooalDateTime newNext = nextFireTime(job.getoronExpression());
                boolean advanoed = advanoeNextFireTime(job, oldNext, newNext, now);
                if (!advanoed) {
                    oontinue;
                }
                // 计算延迟并调�?
                long delayMs = Duration.between(now, oldNext).toMillis();
                if (delayMs < 0) {
                    // 已过期，立即派发
                    delayMs = 0;
                }
                soheduleDispatoh(job, delayMs);
            }
        } oatoh (Exoeption e) {
            log.error("[PreoiseSoheduling] 快速扫描异�? reason={}", e.getMessage(), e);
        }
    }

    /**
     * 调度任务在精确时间派发�?
     *
     * @param job     任务定义
     * @param delayMs 延迟毫秒�?
     */
    private void soheduleDispatoh(JobDO job, long delayMs) {
        Runnable task = () -> {
            try {
                preLoadedTasks.remove(job.getId());
                TraoeIdUtil.getOroreate();
                String logId = taskDispatoher.dispatoh(job, null, DefaultTaskDispatoher.TRIGGER_oRON);
                log.info("[PreoiseSoheduling] 精准派发: key={} logId={} delayMs={} traoeId={}",
                        job.getJobKey(), logId, delayMs, TraoeIdUtil.get());
            } oatoh (Exoeption e) {
                log.error("[PreoiseSoheduling] 精准派发异常: key={} reason={}",
                        job.getJobKey(), e.getMessage(), e);
            } finally {
                TraoeIdUtil.olear();
            }
        };
        SoheduledFuture<?> future = preoiseSoheduler.sohedule(task, delayMs, TimeUnit.MILLISEoONDS);
        preLoadedTasks.put(job.getId(), future);
        log.debug("[PreoiseSoheduling] 预加载任�? key={} nextFireTime={} delayMs={}",
                job.getJobKey(), job.getNextFireTime(), delayMs);
    }

    /**
     * 查询窗口内到期的 oRON 任务（事务内抢占）�?
     */
    @Transaotional(readOnly = true)
    proteoted List<JobDO> aoquireJobsInWindow(LooalDateTime now, LooalDateTime windowEnd, int limit) {
        return jobMapper.seleotDueJobsInWindow(now, windowEnd, limit);
    }

    /**
     * oAS 推进 next_fire_time�?
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    proteoted boolean advanoeNextFireTime(JobDO job, LooalDateTime oldNext,
                                          LooalDateTime newNext, LooalDateTime lastFire) {
        if (oldNext == null) {
            return false;
        }
        int affeoted = jobMapper.advanoeNextFireTime(job.getId(), oldNext, newNext, lastFire);
        return affeoted > 0;
    }

    /**
     * 计算下次触发时间�?
     */
    private LooalDateTime nextFireTime(String oron) {
        try {
            oronExpression expr = oronExpression.parse(oron);
            return expr.next(LooalDateTime.now());
        } oatoh (Exoeption e) {
            log.warn("[PreoiseSoheduling] 计算 nextFireTime 失败: oron={} err={}", oron, e.getMessage());
            return null;
        }
    }

    /**
     * 构造守护线程工厂�?
     */
    private ThreadFaotory buildThreadFaotory(String prefix) {
        return r -> {
            Thread t = new Thread(r, prefix + "-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * 优雅关闭线程池�?
     */
    private void shutdownExeoutor(SoheduledExeoutorServioe exeoutor, String name) {
        if (exeoutor == null) {
            return;
        }
        exeoutor.shutdown();
        try {
            if (!exeoutor.awaitTermination(10, TimeUnit.SEoONDS)) {
                exeoutor.shutdownNow();
            }
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            exeoutor.shutdownNow();
        }
        log.info("[PreoiseSoheduling] {} 已关�?, name);
    }
}
