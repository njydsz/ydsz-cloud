package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 任务定义 Repository（domain 层契约）。
 *
 * <p>定义定时任务定义的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用 DTO，查询入参使用具体字段
 *   <li>分页结果使用内部 {@link PageResult}，禁止 MyBatis-Plus API 透传
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface JobRepository {

  /**
   * 根据 jobKey 查询任务定义。
   *
   * @param jobKey 任务 KEY
   * @return 任务定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobVO> findByJobKey(String jobKey);

  /**
   * 根据 ID 查询任务定义。
   *
   * @param id 任务 ID
   * @return 任务定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobVO> findById(String id);

  /**
   * 查询所有 NORMAL 状态任务（启动时加载）。
   *
   * @return NORMAL 状态任务 VO 列表
   */
  List<JobVO> findAllNormal();

  /**
   * 扫描已到触发时间的 NORMAL 任务（P1-7 Leader 模式专用）。
   *
   * <p>使用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占式行锁。
   *
   * @param now 当前时间
   * @param limit 单批最多扫描任务数
   * @return 待触发任务 VO 列表
   */
  List<JobVO> findDueJobs(LocalDateTime now, int limit);

  /**
   * P0-2: 扫描窗口内到期的 CRON 任务（精准调度预加载）。
   *
   * @param now 当前时间
   * @param windowEnd 窗口结束时间
   * @param limit 单批最多扫描任务数
   * @return 窗口内到期的任务 VO 列表
   */
  List<JobVO> findDueJobsInWindow(LocalDateTime now, LocalDateTime windowEnd, int limit);

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
  Optional<Integer> findConsecutiveFailCount(String id);

  /**
   * P1-5: 查询所有 AUTO_PAUSED 状态且已到自动恢复时间的任务。
   *
   * @param now 当前时间
   * @return 可自动恢复的任务 VO 列表
   */
  List<JobVO> findAutoResumeCandidates(LocalDateTime now);

  /**
   * P1-5: 恢复 AUTO_PAUSED 任务为 NORMAL（重置连续失败计数）。
   *
   * @param id 任务 ID
   * @return 受影响行数
   */
  int resumeAutoPaused(String id);

  // ===== Web 层查询方法（Controller 停止 Mapper 直注） =====

  /**
   * 按任务分组分页查询任务列表（按 created_at 倒序）。
   *
   * <p>仅查询 {@code deleted=0} 的任务。
   *
   * @param jobGroup 任务分组（精确匹配）
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @return 分页结果（records=VO列表, total=总条数）
   */
  PageResult<JobVO> pageByGroup(String jobGroup, int page, int size);

  /**
   * 按任务分组和状态查询任务列表。
   *
   * <p>仅查询 {@code deleted=0} 的任务，按 created_at 倒序。
   *
   * @param jobGroup 任务分组（精确匹配）
   * @param status 任务状态（NORMAL / PAUSED / ERROR / AUTO_PAUSED），可为 null 表示不限
   * @return 任务 VO 列表
   */
  List<JobVO> findByGroupAndStatus(String jobGroup, String status);

  /**
   * 查询所有已删除标记为 0 的任务分组列表（去重）。
   *
   * @return 分组名称列表（按分组名称升序）
   */
  List<String> listDistinctGroups();

  /**
   * 统计指定分组的任务数量。
   *
   * @param jobGroup 任务分组
   * @return 任务数量
   */
  long countByGroup(String jobGroup);

  /**
   * 按任务状态统计数量。
   *
   * @param status 任务状态（NORMAL / PAUSED / ERROR / AUTO_PAUSED）
   * @return 任务数量
   */
  long countByStatus(String status);

  /**
   * 统计所有未删除任务总数。
   *
   * @return 任务总数
   */
  long countAll();

  /**
   * 分页查询任务列表（按关键字/状态/分组过滤，按 created_at 倒序）。
   *
   * <p>仅查询 {@code deleted=0} 的任务。
   *
   * @param keyword 关键字（任务名/KEY/Handler 模糊匹配，可为空）
   * @param status 状态过滤（可为空）
   * @param group 分组过滤（可为空）
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @return 分页结果（records=VO列表, total=总条数）
   */
  PageResult<JobVO> page(String keyword, String status, String group, int page, int size);

  // ===== CUD 操作（入参 DTO，返回影响行数/主键） =====

  /**
   * 新增任务。
   *
   * @param dto 任务创建 DTO
   * @return 新建任务主键 ID
   */
  String insert(JobPostDTO dto);

  /**
   * 按 ID 更新任务。
   *
   * @param dto 任务更新 DTO（必须含 id）
   * @return 受影响行数
   */
  int update(JobPutDTO dto);

  /**
   * 更新任务（通用更新方法，供 Service 层更新任务状态/字段使用）。
   *
   * @param dto 任务更新 DTO（必须含 id）
   * @return 受影响行数
   */
  int putUpdate(JobPutDTO dto);

  /**
   * 按 ID 更新任务状态。
   *
   * @param id 任务 ID
   * @param status 任务状态（NORMAL / PAUSED / ERROR / AUTO_PAUSED）
   * @return 受影响行数
   */
  int updateStatus(String id, String status);

  /**
   * 按 ID 和旧状态原子更新任务状态（CAS）。
   *
   * @param id 任务 ID
   * @param oldStatus 旧状态
   * @param newStatus 新状态
   * @return 受影响行数
   */
  int casUpdateStatus(String id, String oldStatus, String newStatus);

  /**
   * 按 ID 更新任务（直接更新 entity 字段，供调度执行内部使用）。
   *
   * @param vo 任务 VO（必须含 id）
   * @return 受影响行数
   */
  int updateById(JobVO vo);

  /**
   * 按 ID 逻辑删除任务（MyBatis Plus {@code @TableLogic} 自动置 deleted=1）。
   *
   * @param id 任务 ID
   * @return 受影响行数
   */
  int deleteById(String id);

  /**
   * 统计指定条件的任务数量。
   *
   * @param status 状态过滤（可为空）
   * @return 任务数量
   */
  long countByStatusNullable(String status);

  /**
   * 按条件查询任务列表（供诊断等内部场景使用）。
   *
   * @param status 状态过滤（可为空）
   * @return 任务 VO 列表
   */
  List<JobVO> findByStatus(String status);

  /**
   * 分页查询内部结果对象。
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

    /**
     * 转换为 PageResponse（供 Service 层直接返回给 Controller）。
     *
     * <p>注意：此方法丢失了 pageNum/pageSize 信息，仅做简易封装。
     * 建议调用方自行构造 {@code PageResponse} 保留分页完整信息。
     *
     * @return PageResponse 对象
     */
    public PageResponse<List<T>> toPageResponse() {
      PageResponse<List<T>> response = new PageResponse<>();
      response.setCode("A00000");
      response.setMsg("操作成功");
      response.setData(records);
      response.setTotal(total);
      return response;
    }
  }
}
