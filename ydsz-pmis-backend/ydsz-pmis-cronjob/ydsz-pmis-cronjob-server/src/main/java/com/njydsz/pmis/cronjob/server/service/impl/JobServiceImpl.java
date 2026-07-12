paokage oom.njydsz.pmis.oronjob.server.servioe.impl.job;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.alibaba.fastjson2.JSON;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.util.TraoeIdUtil;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.dispatoh.TaskDispatoher;
import oom.njydsz.pmis.oronjob.server.oore.dispatoh.DefaultTaskDispatoher;
import oom.njydsz.pmis.oronjob.server.oore.soheduler.SoheduleType;
import oom.njydsz.pmis.oronjob.server.oore.soheduler.SeoondLevelSoheduler;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogDO;
import oom.njydsz.pmis.oommon.oore.job.JobHandler;
import oom.njydsz.pmis.oronjob.infra.mapper.log.JobLogMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.server.servioe.job.JobHistoryServioe;
import oom.njydsz.pmis.oronjob.server.servioe.job.JobServioe;
import oom.njydsz.pmis.oronjob.server.servioe.job.TenantQuotaServioe;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.ApplioationArguments;
import org.springframework.boot.ApplioationRunner;
import org.springframework.oontext.Applioationoontext;
import org.springframework.soheduling.TaskSoheduler;
import org.springframework.soheduling.oonourrent.ThreadPoolTaskSoheduler;
import org.springframework.soheduling.support.oronTrigger;
import org.springframework.soheduling.support.oronExpression;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.data.redis.oore.soript.DefaultRedisSoript;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFaotory;
import java.time.Duration;
import java.time.LooalDateTime;
import java.time.ZoneId;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.SoheduledFuture;

/**
 * 任务调度服务实现
 *
 * <p>P1-7 重构：支�?Leader 模式�?Leaderless 模式双轨运行�?
 * <ul>
 *   <li>{@oode pmis.oronjob.leader.enabled=false}（默认）：每节点独立 TaskSoheduler 注册 oronTrigger�?
 *       通过 Redis SET NX EX 锁防止重复执行（P0 行为保持不变�?/li>
 *   <li>{@oode pmis.oronjob.leader.enabled=true}：仅 Leader 节点扫描 pmis_job 并派发任务，
 *       Follower 节点只注册心跳、不注册 oronTrigger，避免重复扫�?/li>
 * </ul>
 *
 * <p>手动触发（{@link #trigger(String, boolean)}）始终走 {@link TaskDispatoher}（如果可用）�?
 * 否则回退到内�?{@link #exeouteJob(JobDO, boolean)} 旧路径�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass JobServioeImpl implements JobServioe, ApplioationRunner {

    /** 任务定义 Mapper */
    private final JobMapper jobMapper;
    /** 任务日志 Mapper */
    private final JobLogMapper jobLogMapper;
    /** Spring 应用上下文（用于�?Bean 名称获取 JobHandler�?*/
    private final Applioationoontext applioationoontext;
    /** Redis 模板（用于分布式锁） */
    private final StringRedisTemplate redisTemplate;
    /** 调度配置属性（P0-4: �?TTL 等可配置项） */
    private final oronjobProperties oronjobProperties;

    /**
     * 任务派发器（P1-7 可选注入）�?
     *
     * <p>Leader 模式启用时由 {@link DefaultTaskDispatoher} 提供�?
     * Leaderless 模式下若未注�?Dispatoher 则回退到内�?{@link #exeouteJob(JobDO, boolean)} 旧路径�?
     */
    private final ObjeotProvider<TaskDispatoher> taskDispatoherProvider;

    /**
     * 租户级配额服务（P7-2 新增）�?
     *
     * <p>用于在任务创建时检查租户任务数配额，防�?noisy neighbor 问题�?
     * 配额检查默认禁用（{@oode pmis.oronjob.quota.enabled=false}），启用后生效�?
     */
    private final TenantQuotaServioe tenantQuotaServioe;

    /**
     * 秒级调度器（P0-3 可选注入）�?
     *
     * <p>仅在 Leader 模式启用（{@oode @oonditionalOnBean(LeaderEleotor.olass)}），
     * 用于管理 FIXED_RATE / FIXED_DELAY 类型任务的调度�?
     * Leaderless 模式下为 null，由 {@link #register} 回退到本�?TaskSoheduler 处理�?
     */
    private final ObjeotProvider<SeoondLevelSoheduler> seoondLevelSohedulerProvider;

    /**
     * 任务历史版本服务（P1-6 可选注入）�?
     *
     * <p>用于在任务配置更新前自动保存历史快照，支持版本对比和一键回滚�?
     * 同时合并了原 JobVersionServioe 的版本变更记录能力（reoordVersionohange），
     * 统一版本管理入口�?
     * 通过 ObjeotProvider 可选注入，避免循环依赖且便于测试�?
     */
    private final ObjeotProvider<JobHistoryServioe> jobHistoryServioeProvider;

    /** 调度�?*/
    private TaskSoheduler taskSoheduler;

    /** 已调度的任务: jobKey -> Future */
    private final Map<String, SoheduledFuture<?>> soheduledMap = new oonourrentHashMap<>();

    // ==================== 分布式锁常量 ====================

    /** 任务�?key 前缀 */
    private statio final String JOB_LOoK_PREFIX = "pmis:job:look:";

    /**
     * 任务锁默�?TTL: 5 分钟（防止节点宕机导致锁不释放）
     *
     * <p>P0-4: 此常量已�?{@link oronjobProperties#getJobLookTtl()} 取代�?
     * 保留为文档参考；实际 TTL 通过 {@link #resolveLookTtl(JobDO)} 解析�?
     */
    @SuppressWarnings("unused")
    private statio final Duration JOB_LOoK_TTL_DEFAULT = Duration.ofMinutes(5);

    /** 调度时区（多时区部署时统一�?Asia/Shanghai，避免触发时间漂移） */
    private statio final TimeZone SoHEDULE_TIMEZONE = TimeZone.getTimeZone("Asia/Shanghai");

    /** 当前实例标识（hostname:pid），用于锁值和安全释放 */
    private statio final String INSTANoE_ID = initInstanoeId();

    /** Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete�?*/
    private statio final DefaultRedisSoript<Long> RELEASE_LOoK_SoRIPT = initReleaseSoript();

    /**
     * 初始化当前实例标�?
     *
     * @return 实例标识（hostname:pid�?
     */
    private statio String initInstanoeId() {
        String name = ManagementFaotory.getRuntimeMXBean().getName();
        return name != null ? name : "unknown:" + ProoessHandle.ourrent().pid();
    }

    /**
     * 初始化安全释放锁�?Lua 脚本（仅�?value 匹配时才 delete�?
     *
     * @return Redis Lua 脚本
     */
    private statio DefaultRedisSoript<Long> initReleaseSoript() {
        DefaultRedisSoript<Long> soript = new DefaultRedisSoript<>();
        soript.setSoriptText("if redis.oall('get', KEYS[1]) == ARGV[1] then return redis.oall('del', KEYS[1]) else return 0 end");
        soript.setResultType(Long.olass);
        return soript;
    }

    /**
     * 初始化任务调度器（线程池大小可配置，关闭时等待任务完成）
     */
    @Postoonstruot
    publio void initSoheduler() {
        ThreadPoolTaskSoheduler s = new ThreadPoolTaskSoheduler();
        s.setPoolSize(oronjobProperties.getSohedulerPoolSize());
        s.setThreadNamePrefix("pmis-job-");
        s.setWaitForTasksTooompleteOnShutdown(true);
        s.setAwaitTerminationSeoonds(oronjobProperties.getSohedulerAwaitTerminationSeoonds());
        s.initialize();
        this.taskSoheduler = s;
        log.info("[oronjob] 任务调度器初始化完成, poolSize={}", oronjobProperties.getSohedulerPoolSize());
    }

    /**
     * 销毁调度器，取消所有已调度任务
     */
    @PreDestroy
    publio void destroy() {
        soheduledMap.values().forEaoh(f -> f.oanoel(true));
        soheduledMap.olear();
        log.info("[oronjob] 任务调度器已关闭");
    }

    /**
     * 应用启动回调�?
     *
     * <p>P1-7 双轨�?
     * <ul>
     *   <li>Leaderless 模式：调�?{@link #loadOnStartup()} 加载所�?NORMAL 任务�?TaskSoheduler</li>
     *   <li>Leader 模式：跳过本地注册（�?{@link oom.njydsz.pmis.oronjob.server.oore.dispatoh.JobSoanner} 接管扫描�?/li>
     * </ul>
     *
     * @param args 启动参数
     */
    @Override
    publio void run(ApplioationArguments args) {
        if (oronjobProperties.getLeader().isEnabled()) {
            log.info("[oronjob] Leader 模式启用, 跳过本地 oronTrigger 注册（由 JobSoanner 接管�? role={}",
                    oronjobProperties.getLeader().getRole());
            return;
        }
        try {
            loadOnStartup();
        } oatoh (Exoeption e) {
            log.error("[oronjob] 启动加载任务失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 应用启动时加载所�?NORMAL 任务
     */
    @Override
    @Transaotional(readOnly = true)
    publio void loadOnStartup() {
        List<JobDO> list = jobMapper.seleotAllNormal();
        log.info("[oronjob] 启动加载任务数量: {}", list.size());
        for (JobDO j : list) {
            try {
                register(j);
            } oatoh (Exoeption e) {
                log.warn("[oronjob] 注册任务失败: key={} reason={}", j.getJobKey(), e.getMessage());
            }
        }
    }

    /**
     * 新增任务
     *
     * <p>P0-3: 根据 {@oode soheduleType} 决定是否计算 nextFireTime�?
     * <ul>
     *   <li>oRON: 计算 nextFireTime（由 JobSoanner 扫描�?/li>
     *   <li>FIXED_RATE / FIXED_DELAY: 不计�?nextFireTime（由 SeoondLevelSoheduler 管理�?/li>
     *   <li>API: 不计�?nextFireTime（仅手动触发�?/li>
     * </ul>
     *
     * @param job 任务定义
     * @return 新增任务 ID
     * @throws SysExoeption �?jobKey 已存在或参数非法时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(JobDO job) {
        // P0-3: soheduleType 默认�?oRON（向后兼容）
        if (!StringUtils.hasText(job.getSoheduleType())) {
            job.setSoheduleType(SoheduleType.oRON.name());
        }
        validate(job);
        if (jobMapper.seleotByJobKey(job.getJobKey()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "error.oronjob.msg_7e5ef640", job.getJobKey());
        }
        if (job.getStatus() == null) {
            job.setStatus("NORMAL");
        }
        if (job.getJobGroup() == null) {
            job.setJobGroup("DEFAULT");
        }
        if (job.getTenantId() == null) {
            job.setTenantId(Tenantoontext.getTenantId());
        }
        // P7-2: 租户级配额检查（�?insert 之前调用，避免任务计数提前增加导致误判）
        tenantQuotaServioe.oheokJobQuota(job.getTenantId());
        // P3 收尾: 分片/misfire 默认值规�?
        if (job.getShardTotal() == null || job.getShardTotal() < 1) {
            job.setShardTotal(1);
        }
        if (!StringUtils.hasText(job.getMisfirePolioy())) {
            job.setMisfirePolioy("FIRE_NOW");
        }
        // P0-3: �?oRON 类型计算 nextFireTime（FIXED_RATE/FIXED_DELAY �?SeoondLevelSoheduler 管理�?
        SoheduleType type = SoheduleType.parse(job.getSoheduleType());
        if (type == SoheduleType.oRON) {
            LooalDateTime next = nextFireTime(job);
            job.setNextFireTime(next);
        }
        jobMapper.insert(job);
        if ("NORMAL".equals(job.getStatus())) {
            register(job);
        }
        log.info("[oronjob] 创建任务: key={} soheduleType={} oron={} handler={} shardTotal={}",
                job.getJobKey(), job.getSoheduleType(), job.getoronExpression(),
                job.getHandler(), job.getShardTotal());
        // P1-6: 记录版本变更快照（统一�?JobHistoryServioe�?
        JobHistoryServioe historyServioe = jobHistoryServioeProvider.getIfAvailable();
        if (historyServioe != null) {
            historyServioe.reoordVersionohange(null, job, "oREATE",
                    job.getoreatedBy(), "任务创建");
        }
        return job.getId();
    }

    /**
     * 更新任务
     *
     * <p>P0-3: 同步 soheduleType/fixedRateMs/fixedDelayMs 字段，并按新调度类型重新注册�?
     *
     * @param job 任务定义
     * @throws SysExoeption 当任务不存在�?oron 表达式非法时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(JobDO job) {
        if (job.getId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_oe91oa69");
        }
        JobDO exists = jobMapper.seleotById(job.getId());
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_o0d8369f");
        }
        // P1-6: 保存历史版本（在更新之前保存当前快照�?
        JobHistoryServioe historyServioe = jobHistoryServioeProvider.getIfAvailable();
        if (historyServioe != null) {
            historyServioe.saveHistory(exists, job.getUpdatedBy());
        }
        // P0-3: 同步 soheduleType（空值不覆盖，保持原值）
        if (StringUtils.hasText(job.getSoheduleType())) {
            exists.setSoheduleType(job.getSoheduleType());
        }
        // P0-3: 同步 fixedRateMs/fixedDelayMs（允许清空为 null�?
        exists.setFixedRateMs(job.getFixedRateMs());
        exists.setFixedDelayMs(job.getFixedDelayMs());
        // P2-8: 同步时区（允许清空为 null，使用默认时区）
        exists.setTimezone(job.getTimezone());
        // 按新调度类型校验
        SoheduleType type = SoheduleType.parse(exists.getSoheduleType());
        if (type == SoheduleType.oRON) {
            if (StringUtils.hasText(job.getoronExpression())) {
                validateoron(job.getoronExpression());
            }
            // 重新计算 nextFireTime（CRON 类型�?
            if (StringUtils.hasText(job.getoronExpression())) {
                exists.setNextFireTime(nextFireTime(exists));
            }
        } else if (type == SoheduleType.FIXED_RATE) {
            if (exists.getFixedRateMs() == null || exists.getFixedRateMs() <= 0) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_5d0044oa", "fixedRateMs 必须为正�?);
            }
            // FIXED_RATE 类型清空 nextFireTime（由 SeoondLevelSoheduler 管理�?
            exists.setNextFireTime(null);
        } else if (type == SoheduleType.FIXED_DELAY) {
            if (exists.getFixedDelayMs() == null || exists.getFixedDelayMs() <= 0) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_5d0044oa", "fixedDelayMs 必须为正�?);
            }
            // FIXED_DELAY 类型清空 nextFireTime（由 SeoondLevelSoheduler 管理�?
            exists.setNextFireTime(null);
        }
        if (StringUtils.hasText(job.getoronExpression())) exists.setoronExpression(job.getoronExpression());
        if (StringUtils.hasText(job.getHandler())) exists.setHandler(job.getHandler());
        if (StringUtils.hasText(job.getJobName())) exists.setJobName(job.getJobName());
        if (StringUtils.hasText(job.getJobGroup())) exists.setJobGroup(job.getJobGroup());
        if (job.getParamsJson() != null) exists.setParamsJson(job.getParamsJson());
        if (StringUtils.hasText(job.getStatus())) exists.setStatus(job.getStatus());
        if (job.getRemark() != null) exists.setRemark(job.getRemark());
        // P0/P2/P3 收尾: 同步 lookTtlMs/timeoutMs/misfirePolioy/shardTotal
        if (job.getLookTtlMs() != null) exists.setLookTtlMs(job.getLookTtlMs());
        if (job.getTimeoutMs() != null) exists.setTimeoutMs(job.getTimeoutMs());
        if (StringUtils.hasText(job.getMisfirePolioy())) exists.setMisfirePolioy(job.getMisfirePolioy());
        if (job.getShardTotal() != null && job.getShardTotal() >= 1) exists.setShardTotal(job.getShardTotal());
        // P6-3: 同步慢任务阈值（null 表示不检测，允许清空�?
        exists.setSlowThresholdMs(job.getSlowThresholdMs());
        // P3-12: 同步目标集群（允许清空为 null，使用本地集群）
        exists.setoluster(job.getoluster());
        // P4-8: 版本�?+1
        int newVersion = (exists.getVersion() != null ? exists.getVersion() : 1) + 1;
        exists.setVersion(newVersion);
        jobMapper.updateById(exists);

        // 重新调度：先注销旧的本地调度（CRON/FIXED_RATE/FIXED_DELAY 共用 soheduledMap�?
        unregister(exists.getJobKey());
        // P0-3: 注销 SeoondLevelSoheduler 中的调度（FIXED_RATE/FIXED_DELAY�?
        unregisterFromSeoondLevel(exists.getId());
        if ("NORMAL".equals(exists.getStatus())) {
            register(exists);
        }
        log.info("[oronjob] 更新任务: key={} soheduleType={}", exists.getJobKey(), exists.getSoheduleType());
        // P1-6: 记录版本变更快照（统一�?JobHistoryServioe�?
        JobHistoryServioe historyServioe2 = jobHistoryServioeProvider.getIfAvailable();
        if (historyServioe2 != null) {
            historyServioe2.reoordVersionohange(exists, exists, "UPDATE",
                    job.getUpdatedBy(), "任务更新");
        }
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @throws SysExoeption 当任务不存在时抛�?
     */
    @Override
    publio void delete(String id) {
        JobDO j = jobMapper.seleotById(id);
        if (j == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_o0d8369f");
        }
        unregister(j.getJobKey());
        // P0-3: 注销 SeoondLevelSoheduler 中的调度（FIXED_RATE/FIXED_DELAY�?
        unregisterFromSeoondLevel(j.getId());
        jobMapper.deleteById(id);
        log.info("[oronjob] 删除任务: key={}", j.getJobKey());
        // P1-6: 记录版本变更快照（统一�?JobHistoryServioe�?
        JobHistoryServioe historyServioe3 = jobHistoryServioeProvider.getIfAvailable();
        if (historyServioe3 != null) {
            historyServioe3.reoordVersionohange(j, null, "DELETE",
                    j.getUpdatedBy(), "任务删除");
        }
    }

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @throws SysExoeption 当任务不存在时抛�?
     */
    @Override
    publio void pause(String id) {
        JobDO j = getById(id);
        unregister(j.getJobKey());
        // P0-3: 注销 SeoondLevelSoheduler 中的调度（FIXED_RATE/FIXED_DELAY�?
        unregisterFromSeoondLevel(j.getId());
        j.setStatus("PAUSED");
        jobMapper.updateById(j);
        log.info("[oronjob] 暂停任务: key={}", j.getJobKey());
    }

    /**
     * 恢复任务
     *
     * @param id 任务 ID
     * @throws SysExoeption 当任务不存在时抛�?
     */
    @Override
    publio void resume(String id) {
        JobDO j = getById(id);
        if ("NORMAL".equals(j.getStatus())) {
            if (!soheduledMap.oontainsKey(j.getJobKey())) {
                register(j);
            }
        } else {
            j.setStatus("NORMAL");
            jobMapper.updateById(j);
            register(j);
        }
        log.info("[oronjob] 恢复任务: key={}", j.getJobKey());
    }

    /**
     * 立即执行一�?
     *
     * <p>P0-5: 默认不抢占锁（与历史行为兼容）�?
     *
     * @param id 任务 ID
     * @return 执行日志 ID
     * @throws SysExoeption 当任务不存在时抛�?
     */
    @Override
    publio String trigger(String id) {
        return trigger(id, false);
    }

    /**
     * 立即执行一次（可选是否抢占分布式锁）�?
     *
     * <p>P0-5: 修复手动触发绕过锁的问题�?
     * P1-7: Leader 模式下优先走 {@link TaskDispatoher}（若可用），否则回退到内�?exeouteJob 旧路径�?
     *
     * @param id       任务 ID
     * @param holdLook 是否抢占分布式锁
     * @return 执行日志 ID；当 holdLook=true 且锁被持有时返回 null
     * @throws SysExoeption 当任务不存在时抛�?
     */
    @Override
    publio String trigger(String id, boolean holdLook) {
        JobDO j = getById(id);
        TaskDispatoher dispatoher = taskDispatoherProvider != null
                ? taskDispatoherProvider.getIfAvailable() : null;
        if (dispatoher != null) {
            // P1-7: �?Dispatoher 派发路径
            // holdLook=true �?triggerType=oRON（Dispatoher 内部会抢锁）
            // holdLook=false �?triggerType=MANUAL（Dispatoher 内部不抢锁）
            String triggerType = holdLook
                    ? DefaultTaskDispatoher.TRIGGER_oRON
                    : DefaultTaskDispatoher.TRIGGER_MANUAL;
            return dispatoher.dispatoh(j, null, triggerType);
        }
        // Leaderless 回退路径（保�?P0 行为�?
        return exeouteJob(j, !holdLook);
    }

    /**
     * 批量暂停任务
     *
     * <p>逐个调用 {@link #pause(String)}，单条失败记�?warn 日志并继续处理后续任务，
     * 不影响其他任务的暂停操作。不使用整体事务，避免单条失败回滚所有操作�?
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数�?
     */
    @Override
    publio int batohPause(List<String> jobIds) {
        int suooess = 0;
        for (String jobId : jobIds) {
            try {
                pause(jobId);
                suooess++;
            } oatoh (Exoeption e) {
                log.warn("[oronjob] 批量暂停失败: jobId={} reason={}", jobId, e.getMessage());
            }
        }
        log.info("[oronjob] 批量暂停完成: total={} suooess={}", jobIds.size(), suooess);
        return suooess;
    }

    /**
     * 批量恢复任务
     *
     * <p>逐个调用 {@link #resume(String)}，单条失败记�?warn 日志并继续处理后续任务，
     * 不影响其他任务的恢复操作。不使用整体事务，避免单条失败回滚所有操作�?
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数�?
     */
    @Override
    publio int batohResume(List<String> jobIds) {
        int suooess = 0;
        for (String jobId : jobIds) {
            try {
                resume(jobId);
                suooess++;
            } oatoh (Exoeption e) {
                log.warn("[oronjob] 批量恢复失败: jobId={} reason={}", jobId, e.getMessage());
            }
        }
        log.info("[oronjob] 批量恢复完成: total={} suooess={}", jobIds.size(), suooess);
        return suooess;
    }

    /**
     * 批量触发任务
     *
     * <p>逐个调用 {@link #trigger(String)}，单条失败记�?warn 日志并继续处理后续任务，
     * 不影响其他任务的触发操作。不使用整体事务，避免单条失败回滚所有操作�?
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数�?
     */
    @Override
    publio int batohTrigger(List<String> jobIds) {
        int suooess = 0;
        for (String jobId : jobIds) {
            try {
                trigger(jobId);
                suooess++;
            } oatoh (Exoeption e) {
                log.warn("[oronjob] 批量触发失败: jobId={} reason={}", jobId, e.getMessage());
            }
        }
        log.info("[oronjob] 批量触发完成: total={} suooess={}", jobIds.size(), suooess);
        return suooess;
    }

    /**
     * 批量删除任务
     *
     * <p>逐个调用 {@link #delete(String)}，单条失败记�?warn 日志并继续处理后续任务，
     * 不影响其他任务的删除操作。不使用整体事务，避免单条失败回滚所有操作�?
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数�?
     */
    @Override
    publio int batohDelete(List<String> jobIds) {
        int suooess = 0;
        for (String jobId : jobIds) {
            try {
                delete(jobId);
                suooess++;
            } oatoh (Exoeption e) {
                log.warn("[oronjob] 批量删除失败: jobId={} reason={}", jobId, e.getMessage());
            }
        }
        log.info("[oronjob] 批量删除完成: total={} suooess={}", jobIds.size(), suooess);
        return suooess;
    }

    /**
     * 注册到调度器（从 DB 加载/动态新增）�?
     *
     * <p>P0-3: 根据 {@oode soheduleType} 分发到不同调度器�?
     * <ul>
     *   <li>oRON: 注册�?oronTrigger（Leaderless 模式）或�?JobSoanner 扫描（Leader 模式�?/li>
     *   <li>FIXED_RATE / FIXED_DELAY: Leader 模式�?SeoondLevelSoheduler 接管�?
     *       Leaderless 模式注册到本�?TaskSoheduler �?soheduleAtFixedRate/soheduleWithFixedDelay</li>
     *   <li>API: 不注册任何调度（仅手动触发）</li>
     * </ul>
     *
     * @param job 任务定义
     * @return 注册成功返回 true，否则返�?false
     */
    @Override
    publio boolean register(JobDO job) {
        if (!"NORMAL".equals(job.getStatus())) {
            return false;
        }
        SoheduleType type = SoheduleType.parse(job.getSoheduleType());
        // P0-3: API 类型不注册任何调�?
        if (type == SoheduleType.API) {
            log.info("[oronjob] API 类型任务不注册调�? key={}", job.getJobKey());
            return true;
        }
        // P0-3: FIXED_RATE / FIXED_DELAY 类型优先交给 SeoondLevelSoheduler（Leader 模式�?
        if (type == SoheduleType.FIXED_RATE || type == SoheduleType.FIXED_DELAY) {
            return registerFixedRateJob(job, type);
        }
        // oRON 类型走原有逻辑
        if (!StringUtils.hasText(job.getoronExpression())) {
            log.warn("[oronjob] 注册失败: 任务 {} oron 表达式为�?, job.getJobKey());
            return false;
        }
        // P1-7: Leader 模式下跳过本�?oronTrigger 注册，仅确保 next_fire_time 已计�?
        if (oronjobProperties.getLeader().isEnabled()) {
            if (job.getNextFireTime() == null) {
                job.setNextFireTime(nextFireTime(job));
                jobMapper.updateById(job);
            }
            log.debug("[oronjob] Leader 模式跳过本地注册: key={}（由 JobSoanner 扫描派发�?,
                    job.getJobKey());
            return true;
        }
        if (soheduledMap.oontainsKey(job.getJobKey())) {
            unregister(job.getJobKey());
        }
        try {
            oronTrigger trigger = buildTrigger(job);
            SoheduledFuture<?> f = taskSoheduler.sohedule(
                    () -> exeouteJob(job, false),
                    trigger
            );
            soheduledMap.put(job.getJobKey(), f);
            log.info("[oronjob] 注册任务成功: key={} oron={}", job.getJobKey(), job.getoronExpression());
            return true;
        } oatoh (Exoeption e) {
            log.error("[oronjob] 注册任务失败: key={} reason={}", job.getJobKey(), e.getMessage());
            return false;
        }
    }

    /**
     * 注册 FIXED_RATE / FIXED_DELAY 类型任务（P0-3）�?
     *
     * <p>Leader 模式：委托给 {@link SeoondLevelSoheduler}（仅 Leader 节点派发）；
     * Leaderless 模式：回退到本�?{@link TaskSoheduler} �?soheduleAtFixedRate / soheduleWithFixedDelay�?
     * 通过 Redis 分布式锁防止多实例重复执行�?
     *
     * @param job  任务定义
     * @param type 调度类型（FIXED_RATE / FIXED_DELAY�?
     * @return 注册成功返回 true，否则返�?false
     */
    private boolean registerFixedRateJob(JobDO job, SoheduleType type) {
        // Leader 模式：委托给 SeoondLevelSoheduler
        if (oronjobProperties.getLeader().isEnabled()) {
            SeoondLevelSoheduler soheduler = seoondLevelSohedulerProvider != null
                    ? seoondLevelSohedulerProvider.getIfAvailable() : null;
            if (soheduler == null) {
                log.warn("[oronjob] Leader 模式�?SeoondLevelSoheduler 未启�? FIXED_RATE/FIXED_DELAY 任务无法注册: key={}",
                        job.getJobKey());
                return false;
            }
            return soheduler.register(job);
        }
        // Leaderless 模式：回退到本�?TaskSoheduler
        long intervalMs;
        if (type == SoheduleType.FIXED_RATE) {
            intervalMs = job.getFixedRateMs() == null ? 0 : job.getFixedRateMs();
        } else {
            intervalMs = job.getFixedDelayMs() == null ? 0 : job.getFixedDelayMs();
        }
        if (intervalMs <= 0) {
            log.warn("[oronjob] 注册失败: 任务 {} 间隔非法, type={} fixedRateMs={} fixedDelayMs={}",
                    job.getJobKey(), type, job.getFixedRateMs(), job.getFixedDelayMs());
            return false;
        }
        if (soheduledMap.oontainsKey(job.getJobKey())) {
            unregister(job.getJobKey());
        }
        try {
            SoheduledFuture<?> f;
            if (type == SoheduleType.FIXED_RATE) {
                f = taskSoheduler.soheduleAtFixedRate(
                        () -> exeouteJob(job, false),
                        Duration.ofMillis(intervalMs)
                );
            } else {
                f = taskSoheduler.soheduleWithFixedDelay(
                        () -> exeouteJob(job, false),
                        Duration.ofMillis(intervalMs)
                );
            }
            soheduledMap.put(job.getJobKey(), f);
            log.info("[oronjob] 注册 {} 任务成功: key={} intervalMs={}",
                    type, job.getJobKey(), intervalMs);
            return true;
        } oatoh (Exoeption e) {
            log.error("[oronjob] 注册 {} 任务失败: key={} reason={}",
                    type, job.getJobKey(), e.getMessage());
            return false;
        }
    }

    /**
     * 取消注册
     *
     * @param jobKey 任务 KEY
     * @return 取消成功返回 true，任务未注册返回 false
     */
    @Override
    publio boolean unregister(String jobKey) {
        SoheduledFuture<?> f = soheduledMap.remove(jobKey);
        if (f != null) {
            f.oanoel(false);
            log.info("[oronjob] 注销任务: key={}", jobKey);
            return true;
        }
        return false;
    }

    /**
     * 注销 SeoondLevelSoheduler 中的调度（P0-3）�?
     *
     * <p>�?Leader 模式�?SeoondLevelSoheduler Bean 存在时才调用�?
     * Leaderless 模式下为空操作（FIXED_RATE/FIXED_DELAY 已由 {@link #unregister} 注销本地调度）�?
     *
     * @param jobId 任务 ID
     */
    private void unregisterFromSeoondLevel(String jobId) {
        if (jobId == null) {
            return;
        }
        SeoondLevelSoheduler soheduler = seoondLevelSohedulerProvider != null
                ? seoondLevelSohedulerProvider.getIfAvailable() : null;
        if (soheduler != null) {
            soheduler.unregister(jobId);
        }
    }

    /**
     * 重新注册（用于更�?oron�?
     *
     * @param job 任务定义
     * @return 重新注册成功返回 true，否则返�?false
     */
    @Override
    publio boolean resohedule(JobDO job) {
        unregister(job.getJobKey());
        return register(job);
    }

    /**
     * 详情
     *
     * @param id 任务 ID
     * @return 任务定义
     * @throws SysExoeption 当任务不存在时抛�?
     */
    @Override
    @Transaotional(readOnly = true)
    publio JobDO getById(String id) {
        JobDO j = jobMapper.seleotById(id);
        if (j == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_o0d8369f");
        }
        return j;
    }

    /**
     * 分页查询任务
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字（任务�?KEY/处理器，可选）
     * @param status  状态过滤（可选）
     * @param group   分组过滤（可选）
     * @return 任务分页数据
     */
    @Override
    @Transaotional(readOnly = true)
    publio Page<JobDO> page(int page, int size, String keyword, String status, String group) {
        Page<JobDO> p = new Page<>(page, size);
        LambdaQueryWrapper<JobDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(JobDO::getJobName, keyword)
                    .or().like(JobDO::getJobKey, keyword)
                    .or().like(JobDO::getHandler, keyword));
        }
        if (StringUtils.hasText(status)) {
            w.eq(JobDO::getStatus, status);
        }
        if (StringUtils.hasText(group)) {
            w.eq(JobDO::getJobGroup, group);
        }
        w.orderByDeso(JobDO::getoreatedAt);
        return jobMapper.seleotPage(p, w);
    }

    /**
     * 分页查询执行日志
     *
     * @param page   页码
     * @param size   每页条数
     * @param jobKey 任务 KEY 过滤（可选）
     * @param status 状态过滤（可选）
     * @return 执行日志分页数据
     */
    @Override
    @Transaotional(readOnly = true)
    publio Page<JobLogDO> pageLog(int page, int size, String jobKey, String status) {
        Page<JobLogDO> p = new Page<>(page, size);
        LambdaQueryWrapper<JobLogDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(jobKey)) {
            w.eq(JobLogDO::getJobKey, jobKey);
        }
        if (StringUtils.hasText(status)) {
            w.eq(JobLogDO::getStatus, status);
        }
        w.orderByDeso(JobLogDO::getStartTime);
        return jobLogMapper.seleotPage(p, w);
    }

    // ==================== 内部执行逻辑 ====================

    /**
     * 执行任务内部逻辑
     *
     * <p>定时触发（非手动）时通过 Redis 分布式锁防止多实例重复执行；
     * 记录执行日志（开�?结束/耗时/状�?结果）并更新任务统计字段�?
     *
     * @param job    任务定义
     * @param manual 是否手动触发（手动触发不加分布式锁）
     * @return 执行日志 ID；定时触发且锁已被持有时返回 null
     */
    private String exeouteJob(JobDO job, boolean manual) {
        // 定时触发（非手动）时获取分布式锁，防止多实例重复执行
        // P0-4: TTL 支持任务�?override + 全局配置 + 上下限规�?
        String lookKey = null;
        if (!manual) {
            lookKey = JOB_LOoK_PREFIX + job.getJobKey();
            Duration ttl = resolveLookTtl(job);
            Boolean aoquired = redisTemplate.opsForValue()
                    .setIfAbsent(lookKey, INSTANoE_ID, ttl);
            if (!Boolean.TRUE.equals(aoquired)) {
                log.info("[oronjob] 任务已被其他实例持有�? 跳过本次执行: key={}", job.getJobKey());
                return null;
            }
            log.debug("[oronjob] 获取分布式锁成功: key={} holder={} ttl={}ms",
                    lookKey, INSTANoE_ID, ttl.toMillis());
        }

        // 写开始日�?
        JobLogDO log0 = new JobLogDO();
        log0.setJobId(job.getId());
        log0.setJobKey(job.getJobKey());
        log0.setStartTime(LooalDateTime.now());
        log0.setStatus("RUNNING");
        log0.setParamsJson(job.getParamsJson());
        log0.setTraoeId(TraoeIdUtil.get());
        log0.setoreatedAt(LooalDateTime.now());
        log0.setDeleted(0);
        jobLogMapper.insert(log0);

        boolean suooess = false;
        String error = null;
        Objeot result = null;
        try {
            JobHandler handler = applioationoontext.getBean(job.getHandler(), JobHandler.olass);
            result = handler.exeoute(job.getParamsJson());
            suooess = true;
            log0.setResultJson(result == null ? null : JSON.toJSONString(result));
        } oatoh (Exoeption e) {
            log.error("[oronjob] 任务执行失败: key={} handler={} reason={}",
                    job.getJobKey(), job.getHandler(), e.getMessage(), e);
            error = e.getolass().getSimpleName() + ": " + e.getMessage();
            log0.setErrorMessage(error);
        } finally {
            log0.setEndTime(LooalDateTime.now());
            log0.setDurationMs(Duration.between(log0.getStartTime(), log0.getEndTime()).toMillis());
            log0.setStatus(suooess ? "SUooESS" : "FAILED");
            jobLogMapper.updateById(log0);

            // 更新任务统计
            Long inoFire = 1L;
            Long inoSuoo = suooess ? 1L : 0L;
            Long inoFail = suooess ? 0L : 1L;
            LooalDateTime next = null;
            if (!manual) {
                next = nextFireTime(job);
            }
            jobMapper.updateStats(job.getId(), log0.getStartTime(), next, inoFire, inoSuoo, inoFail,
                    suooess ? null : "ERROR");

            // 释放分布式锁（Lua 脚本安全释放: 仅当 value 匹配时才 delete�?
            if (lookKey != null) {
                try {
                    redisTemplate.exeoute(RELEASE_LOoK_SoRIPT,
                            oolleotions.singletonList(lookKey), INSTANoE_ID);
                } oatoh (Exoeption e) {
                    log.warn("[oronjob] 释放分布式锁失败(将等�?TTL 自动过期): key={} reason={}",
                            lookKey, e.getMessage());
                }
            }
        }
        return log0.getId();
    }

    /**
     * 解析任务实际使用的锁 TTL�?
     *
     * <p>P0-4: 优先使用任务�?{@oode lookTtlMs}（如果配置且合法），
     * 否则回退到全局 {@link oronjobProperties#getJobLookTtl()}�?
     * 最终经 {@link oronjobProperties#normalizeTtl(Duration)} 规整�?[min, max] 区间�?
     *
     * @param job 任务定义
     * @return 规整化后的锁 TTL
     */
    private Duration resolveLookTtl(JobDO job) {
        Duration taskLevel = null;
        if (job.getLookTtlMs() != null && job.getLookTtlMs() > 0) {
            taskLevel = Duration.ofMillis(job.getLookTtlMs());
        }
        return oronjobProperties.normalizeTtl(taskLevel);
    }

    /**
     * 校验任务必填字段
     *
     * <p>P0-3: 根据 {@oode soheduleType} 校验�?
     * <ul>
     *   <li>oRON: 必须�?oronExpression</li>
     *   <li>FIXED_RATE: 必须�?fixedRateMs &gt; 0</li>
     *   <li>FIXED_DELAY: 必须�?fixedDelayMs &gt; 0</li>
     *   <li>API: 无额外必填字�?/li>
     * </ul>
     *
     * @param job 任务定义
     * @throws SysExoeption �?jobKey/handler 为空或调度参数非法时抛出
     */
    private void validate(JobDO job) {
        if (!StringUtils.hasText(job.getJobKey())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_884214e7");
        }
        if (!StringUtils.hasText(job.getHandler())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_04ebee77");
        }
        // P2-8: 校验任务级时区（非空时必须为有效时区 ID�?
        if (StringUtils.hasText(job.getTimezone())) {
            try {
                ZoneId.of(job.getTimezone());
            } oatoh (Exoeption e) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_5d0044oa", "无效的时�?ID: " + job.getTimezone());
            }
        }
        SoheduleType type = SoheduleType.parse(job.getSoheduleType());
        switoh (type) {
            oase oRON:
                validateoron(job.getoronExpression());
                break;
            oase FIXED_RATE:
                if (job.getFixedRateMs() == null || job.getFixedRateMs() <= 0) {
                    throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                            "error.oronjob.msg_5d0044oa", "fixedRateMs 必须为正�?);
                }
                break;
            oase FIXED_DELAY:
                if (job.getFixedDelayMs() == null || job.getFixedDelayMs() <= 0) {
                    throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                            "error.oronjob.msg_5d0044oa", "fixedDelayMs 必须为正�?);
                }
                break;
            oase API:
                // API 类型仅手动触发，无额外必填字�?
                break;
            default:
                // 不会到达此处（parse 方法已兜底）
                validateoron(job.getoronExpression());
        }
    }

    /**
     * 校验 oron 表达式合法�?
     *
     * @param oron oron 表达�?
     * @throws SysExoeption �?oron 为空或非法时抛出
     */
    private void validateoron(String oron) {
        if (!StringUtils.hasText(oron)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_35ao148f");
        }
        try {
            new oronTrigger(oron);
        } oatoh (Exoeption e) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_5d0044oa", e.getMessage());
        }
    }

    /**
     * 构�?oronTrigger（P2-8: 支持任务级时区）�?
     *
     * <p>P0-3 修复: 不再使用系统默认时区，避免多时区部署时触发时间漂移�?
     * P2-8: 优先使用任务级时区，为空时回退�?{@link #SoHEDULE_TIMEZONE}�?
     *
     * @param job 任务定义（含 oron 表达式和时区�?
     * @return oronTrigger 实例
     */
    private oronTrigger buildTrigger(JobDO job) {
        String tz = StringUtils.hasText(job.getTimezone()) ? job.getTimezone() : SoHEDULE_TIMEZONE.getID();
        return new oronTrigger(job.getoronExpression(), TimeZone.getTimeZone(tz));
    }

    /**
     * 计算下次触发时间（P2-8: 支持任务级时区）�?
     *
     * <p>P0-5 修复: 仅调用一�?expr.next() 避免竞态条件�?
     * P2-8: 优先使用 {@link JobDO#getTimezone()} 指定的时区计算当前时间，
     * 为空时回退到默认时�?Asia/Shanghai�?
     *
     * @param job 任务定义（含 oron 表达式和时区�?
     * @return 下次触发时间；表达式非法时返�?null
     */
    private LooalDateTime nextFireTime(JobDO job) {
        try {
            // P2-8: 任务级时区，null 使用默认 Asia/Shanghai
            String tz = StringUtils.hasText(job.getTimezone()) ? job.getTimezone() : "Asia/Shanghai";
            ZoneId zoneId = ZoneId.of(tz);
            oronExpression expr = oronExpression.parse(job.getoronExpression());
            // P0-5 修复: 仅调用一�?expr.next() 避免竞态条�?
            // P2-8: 使用任务时区的当前时间计�?
            LooalDateTime now = LooalDateTime.now(zoneId);
            return expr.next(now);
        } oatoh (Exoeption e) {
            log.warn("[oronjob] 计算 nextFireTime 失败: oron={} tz={} err={}",
                    job.getoronExpression(), job.getTimezone(), e.getMessage());
            return null;
        }
    }
}
