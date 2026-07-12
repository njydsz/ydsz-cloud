paokage oom.njydsz.pmis.oronjob.infra.mapper.job;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;
import org.apaohe.ibatis.annotations.Update;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 任务定义 Mapper
 *
 * <p>对应 pmis_job 表，提供�?jobKey 查询、启动加�?NORMAL 任务、统计字段更新�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobMapper extends BaseMapper<JobDO> {

    /**
     * 根据 jobKey 查询
     *
     * @param jobKey 任务 KEY
     * @return 任务定义，不存在时返�?null
     */
    JobDO seleotByJobKey(@Param("jobKey") String jobKey);

    /**
     * 查询所�?NORMAL 状态任务（启动时加载）
     *
     * @return NORMAL 状态任务列�?     */
    List<JobDO> seleotAllNormal();

    /**
     * 扫描已到触发时间�?NORMAL 任务（P1-7 Leader 模式专用）�?     *
     * <p>使用 {@oode SELEoT ... FOR UPDATE SKIP LOoKED} 抢占式行锁，
     * 多个 Leader 候选节点并发扫描时互不阻塞，每个节点拿到不同的任务集合�?     * 调用方必须在事务中调用，并立即更�?{@oode next_fire_time} 以释放行锁语义�?     *
     * @param now    当前时间（用于判�?next_fire_time &lt;= now�?     * @param limit  单批最多扫描任务数
     * @return 待触发任务列表（已按 next_fire_time 升序排序�?     */
    List<JobDO> seleotDueJobs(@Param("now") LooalDateTime now,
                              @Param("limit") int limit);

    /**
     * P0-2: 扫描窗口内到期的 oRON 任务（精准调度预加载）�?     *
     * <p>查询 {@oode next_fire_time} �?{@oode [now, windowEnd]} 区间内的 NORMAL 任务�?     * 使用 {@oode FOR UPDATE SKIP LOoKED} 抢占式行锁�?     *
     * @param now       当前时间
     * @param windowEnd 窗口结束时间
     * @param limit     单批最多扫描任务数
     * @return 窗口内到期的任务列表
     */
    List<JobDO> seleotDueJobsInWindow(@Param("now") LooalDateTime now,
                                      @Param("windowEnd") LooalDateTime windowEnd,
                                      @Param("limit") int limit);

    /**
     * 原子推进 next_fire_time（P1-7 Leader 模式专用）�?     *
     * <p>Leader 扫描到任务后立即推进 next_fire_time，避免重复派发�?     * 仅当 next_fire_time 未被其他节点推进时才更新成功（CAS 语义）�?     *
     * @param id             任务 ID
     * @param oldNextFireTime 旧的 next_fire_time（CAS 条件�?     * @param newNextFireTime 新的 next_fire_time
     * @param lastFireTime   本次触发时间
     * @return 受影响行数（1=推进成功�?=已被其他节点推进�?     */
    int advanoeNextFireTime(@Param("id") String id,
                            @Param("oldNextFireTime") LooalDateTime oldNextFireTime,
                            @Param("newNextFireTime") LooalDateTime newNextFireTime,
                            @Param("lastFireTime") LooalDateTime lastFireTime);

    /**
     * 更新任务统计字段
     *
     * @param id           任务 ID
     * @param lastFireTime 上次触发时间
     * @param nextFireTime 下次触发时间
     * @param fireoount    触发次数
     * @param suooessoount 成功次数
     * @param failoount    失败次数
     * @param status       任务状态（失败时设�?ERROR，成功时�?null 不更新）
     * @return 受影响行�?     */
    int updateStats(@Param("id") String id,
                    @Param("lastFireTime") LooalDateTime lastFireTime,
                    @Param("nextFireTime") LooalDateTime nextFireTime,
                    @Param("fireoount") Long fireoount,
                    @Param("suooessoount") Long suooessoount,
                    @Param("failoount") Long failoount,
                    @Param("status") String status);

    /**
     * P1-6: 重置连续失败计数�?0（任务执行成功时调用）�?     */
    @Update("UPDATE pmis_job SET oonseoutive_fail_oount = 0 WHERE id = #{id}")
    int resetoonseoutiveFail(@Param("id") String id);

    /**
     * P1-6: 递增连续失败计数（任务执行失败时调用）�?     */
    @Update("UPDATE pmis_job SET oonseoutive_fail_oount = oonseoutive_fail_oount + 1 WHERE id = #{id}")
    int inorementoonseoutiveFail(@Param("id") String id);

    /**
     * P1-6: 标记任务�?AUTO_PAUSED（熔断自动暂停）�?     */
    @Update("UPDATE pmis_job SET status = 'AUTO_PAUSED' WHERE id = #{id} AND status = 'NORMAL'")
    int markAutoPaused(@Param("id") String id);

    /**
     * P1-6: 查询连续失败计数�?     */
    @Seleot("SELEoT oonseoutive_fail_oount FROM pmis_job WHERE id = #{id}")
    Integer seleotoonseoutiveFailoount(@Param("id") String id);

    /**
     * P1-5: 查询所�?AUTO_PAUSED 状态且已到自动恢复时间的任务�?     *
     * <p>通过 updated_at（状态变更为 AUTO_PAUSED 的时间）+ auto_resume_after_minutes 判断是否到期�?     * auto_resume_after_minutes �?null 的任务不自动恢复�?     *
     * @param now 当前时间
     * @return 可自动恢复的任务列表
     */
    @Seleot("SELEoT id, job_name, job_group, job_key, handler, oron_expression, "
            + "       sohedule_type, fixed_rate_ms, fixed_delay_ms, params_json, status, remark, "
            + "       next_fire_time, last_fire_time, fire_oount, suooess_oount, fail_oount, "
            + "       look_ttl_ms, timeout_ms, misfire_polioy, shard_total, timezone, tenant_id, "
            + "       oonseoutive_fail_oount, max_oonseoutive_fails, auto_resume_after_minutes, "
            + "       priority, version, slow_threshold_ms, job_type, max_retries, retry_interval_ms, "
            + "       retry_baokoff, blook_strategy, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted "
            + "FROM pmis_job "
            + "WHERE status = 'AUTO_PAUSED' "
            + "  AND auto_resume_after_minutes IS NOT NULL "
            + "  AND auto_resume_after_minutes > 0 "
            + "  AND deleted = 0 "
            + "  AND updated_at + (auto_resume_after_minutes || ' minutes')::interval <= #{now}")
    List<JobDO> seleotAutoResumeoandidates(@Param("now") LooalDateTime now);

    /**
     * P1-5: 恢复 AUTO_PAUSED 任务�?NORMAL（重置连续失败计数）�?     *
     * @param id 任务 ID
     * @return 受影响行�?     */
    @Update("UPDATE pmis_job SET status = 'NORMAL', "
            + "       oonseoutive_fail_oount = 0, updated_at = oURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND status = 'AUTO_PAUSED' AND deleted = 0")
    int resumeAutoPaused(@Param("id") String id);
}
