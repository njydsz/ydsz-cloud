package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.infra.entity.job.Job;

/**
 * 任务定义 Repository。
 *
 * <p>封装 {@code ydsz_job} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobRepository {

  /**
   * 根据 jobKey 查询任务定义。
   *
   * @param jobKey 任务 KEY
   * @return 任务定义，不存在时返回 null
   */
  Job selectByJobKey(String jobKey);

  /**
   * 查询所有 NORMAL 状态任务（启动时加载）。
   *
   * @return NORMAL 状态任务列表
   */
  List<Job> selectAllNormal();

  /**
   * 扫描已到触发时间的 NORMAL 任务（P1-7 Leader 模式专用）。
   *
   * <p>使用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占式行锁。
   *
   * @param now 当前时间
   * @param limit 单批最多扫描任务数
   * @return 待触发任务列表
   */
  List<Job> selectDueJobs(LocalDateTime now, int limit);

  /**
   * P0-2: 扫描窗口内到期的 CRON 任务（精准调度预加载）。
   *
   * @param now 当前时间
   * @param windowEnd 窗口结束时间
   * @param limit 单批最多扫描任务数
   * @return 窗口内到期的任务列表
   */
  List<Job> selectDueJobsInWindow(LocalDateTime now, LocalDateTime windowEnd, int limit);

  /**
   * 原子推进 next_fire_time（P1-7 Leader 模式专用）。
   *
   * @param id 任务 ID
   * @param oldNextFireTime 旧的 next_fire_time（CAS 条件）
   * @param newNextFireTime 新的 next_fire_time
   * @param lastFireTime 本次触发时间
   * @return 受影响行数（1=推进成功；0=已被其他节点推进）
   */
  int advanceNextFireTime(
      String id,
      LocalDateTime oldNextFireTime,
      LocalDateTime newNextFireTime,
      LocalDateTime lastFireTime);

  /**
   * 更新任务统计字段。
   *
   * @param id 任务 ID
   * @param lastFireTime 上次触发时间
   * @param nextFireTime 下次触发时间
   * @param fireCount 触发次数
   * @param successCount 成功次数
   * @param failCount 失败次数
   * @param status 任务状态
   * @return 受影响行数
   */
  int updateStats(
      String id,
      LocalDateTime lastFireTime,
      LocalDateTime nextFireTime,
      Long fireCount,
      Long successCount,
      Long failCount,
      String status);

  /**
   * P1-6: 重置连续失败计数为 0。
   *
   * @param id 任务 ID
   * @return 受影响行数
   */
  int resetConsecutiveFail(String id);

  /**
   * P1-6: 递增连续失败计数。
   *
   * @param id 任务 ID
   * @return 受影响行数
   */
  int incrementConsecutiveFail(String id);

  /**
   * P1-6: 标记任务为 AUTO_PAUSED（熔断自动暂停）。
   *
   * @param id 任务 ID
   * @return 受影响行数
   */
  int markAutoPaused(String id);

  /**
   * P1-6: 查询连续失败计数。
   *
   * @param id 任务 ID
   * @return 连续失败计数
   */
  Integer selectConsecutiveFailCount(String id);

  /**
   * P1-5: 查询所有 AUTO_PAUSED 状态且已到自动恢复时间的任务。
   *
   * @param now 当前时间
   * @return 可自动恢复的任务列表
   */
  List<Job> selectAutoResumeCandidates(LocalDateTime now);

  /**
   * P1-5: 恢复 AUTO_PAUSED 任务为 NORMAL（重置连续失败计数）。
   *
   * @param id 任务 ID
   * @return 受影响行数
   */
  int resumeAutoPaused(String id);

  /**
   * 分页结果封装（供 Web 层查询方法返回分页数据）。
   *
   * <p>P0-FIX：本接口（infra 层）与 domain 层 {@code JobRepository} 同名，infra.repository.impl 包内的
   * 实现类引用 {@code JobRepository.PageResult} 时会优先解析到同包本接口；原缺少数该嵌套类型导致编译失败
   * （cannot find symbol: JobRepository.PageResult）。补充与 domain 版一致的实现。
   *
   * @param <T> 记录类型
   */
  class PageResult<T> {
    private final List<T> records;
    private final long total;

    public PageResult(List<T> records, long total) {
      this.records = records;
      this.total = total;
    }

    public List<T> getRecords() {
      return records;
    }

    public long getTotal() {
      return total;
    }
  }
}
