package com.njydsz.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.cronjob.infra.entity.job.Job;

/**
 * 任务定义 Mapper
 *
 * <p>对应 ydsz_job 表，提供按 jobKey 查询、启动加载 NORMAL 任务、统计字段更新。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface JobMapper extends BaseMapper<Job> {

  /**
   * 根据 jobKey 查询
   *
   * @param jobKey 任务 KEY
   * @return 任务定义，不存在时返回 null
   */
  Job selectByJobKey(@Param("jobKey") String jobKey);

  /**
   * 查询所有 NORMAL 状态任务（启动时加载）
   *
   * @return NORMAL 状态任务列表
   */
  List<Job> selectAllNormal();

  /**
   * 扫描已到触发时间的 NORMAL 任务（P1-7 Leader 模式专用）。
   *
   * <p>使用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占式行锁， 多个 Leader 候选节点并发扫描时互不阻塞，每个节点拿到不同的任务集合。
   * 调用方必须在事务中调用，并立即更新 {@code next_fire_time} 以释放行锁语义。
   *
   * @param now 当前时间（用于判断 next_fire_time &lt;= now）
   * @param limit 单批最多扫描任务数
   * @return 待触发任务列表（已按 next_fire_time 升序排序）
   */
  List<Job> selectDueJobs(@Param("now") LocalDateTime now, @Param("limit") int limit);

  /**
   * P0-2: 扫描窗口内到期的 CRON 任务（精准调度预加载）。
   *
   * <p>查询 {@code next_fire_time} 在 {@code [now, windowEnd]} 区间内的 NORMAL 任务， 使用 {@code FOR UPDATE
   * SKIP LOCKED} 抢占式行锁。
   *
   * @param now 当前时间
   * @param windowEnd 窗口结束时间
   * @param limit 单批最多扫描任务数
   * @return 窗口内到期的任务列表
   */
  List<Job> selectDueJobsInWindow(
      @Param("now") LocalDateTime now,
      @Param("windowEnd") LocalDateTime windowEnd,
      @Param("limit") int limit);

  /**
   * 原子推进 next_fire_time（P1-7 Leader 模式专用）。
   *
   * <p>Leader 扫描到任务后立即推进 next_fire_time，避免重复派发。 仅当 next_fire_time 未被其他节点推进时才更新成功（CAS 语义）。
   *
   * @param id 任务 ID
   * @param oldNextFireTime 旧的 next_fire_time（CAS 条件）
   * @param newNextFireTime 新的 next_fire_time
   * @param lastFireTime 本次触发时间
   * @return 受影响行数（1=推进成功；0=已被其他节点推进）
   */
  int advanceNextFireTime(
      @Param("id") String id,
      @Param("oldNextFireTime") LocalDateTime oldNextFireTime,
      @Param("newNextFireTime") LocalDateTime newNextFireTime,
      @Param("lastFireTime") LocalDateTime lastFireTime);

  /**
   * 更新任务统计字段
   *
   * @param id 任务 ID
   * @param lastFireTime 上次触发时间
   * @param nextFireTime 下次触发时间
   * @param fireCount 触发次数
   * @param successCount 成功次数
   * @param failCount 失败次数
   * @param status 任务状态（失败时设为 ERROR，成功时传 null 不更新）
   * @return 受影响行数
   */
  int updateStats(
      @Param("id") String id,
      @Param("lastFireTime") LocalDateTime lastFireTime,
      @Param("nextFireTime") LocalDateTime nextFireTime,
      @Param("fireCount") Long fireCount,
      @Param("successCount") Long successCount,
      @Param("failCount") Long failCount,
      @Param("status") String status);

  /** P1-6: 重置连续失败计数为 0（任务执行成功时调用）。 */
  @Update("UPDATE ydsz_job SET consecutive_fail_count = 0 WHERE id = #{id}")
  int resetConsecutiveFail(@Param("id") String id);

  /** P1-6: 递增连续失败计数（任务执行失败时调用）。 */
  @Update(
      "UPDATE ydsz_job SET consecutive_fail_count = consecutive_fail_count + 1 WHERE id = #{id}")
  int incrementConsecutiveFail(@Param("id") String id);

  /** P1-6: 标记任务为 AUTO_PAUSED（熔断自动暂停）。 */
  @Update("UPDATE ydsz_job SET status = 'AUTO_PAUSED' WHERE id = #{id} AND status = 'NORMAL'")
  int markAutoPaused(@Param("id") String id);

  /** P1-6: 查询连续失败计数。 */
  @Select("SELECT consecutive_fail_count FROM ydsz_job WHERE id = #{id}")
  Integer selectConsecutiveFailCount(@Param("id") String id);

  /**
   * P1-5: 查询所有 AUTO_PAUSED 状态且已到自动恢复时间的任务。
   *
   * <p>通过 updated_at（状态变更为 AUTO_PAUSED 的时间）+ auto_resume_after_minutes 判断是否到期。
   * auto_resume_after_minutes 为 null 的任务不自动恢复。
   *
   * @param now 当前时间
   * @return 可自动恢复的任务列表
   */
  @Select(
      "SELECT id, job_name, job_group, job_key, handler, cron_expression, "
          + "       schedule_type, fixed_rate_ms, fixed_delay_ms, params_json, status, remark, "
          + "       next_fire_time, last_fire_time, fire_count, success_count, fail_count, "
          + "       lock_ttl_ms, timeout_ms, misfire_policy, shard_total, timezone, tenant_id, "
          + "       consecutive_fail_count, max_consecutive_fails, auto_resume_after_minutes, "
          + "       priority, version, slow_threshold_ms, job_type, max_retries, retry_interval_ms, "
          + "       retry_backoff, block_strategy, "
          + "       created_by, created_at, updated_by, updated_at, deleted "
          + "FROM ydsz_job "
          + "WHERE status = 'AUTO_PAUSED' "
          + "  AND auto_resume_after_minutes IS NOT NULL "
          + "  AND auto_resume_after_minutes > 0 "
          + "  AND deleted = 0 "
          + "  AND updated_at + (auto_resume_after_minutes || ' minutes')::interval <= #{now}")
  List<Job> selectAutoResumeCandidates(@Param("now") LocalDateTime now);

  /**
   * P1-5: 恢复 AUTO_PAUSED 任务为 NORMAL（重置连续失败计数）。
   *
   * @param id 任务 ID
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job SET status = 'NORMAL', "
          + "       consecutive_fail_count = 0, updated_at = CURRENT_TIMESTAMP "
          + "WHERE id = #{id} AND status = 'AUTO_PAUSED' AND deleted = 0")
  int resumeAutoPaused(@Param("id") String id);
}
