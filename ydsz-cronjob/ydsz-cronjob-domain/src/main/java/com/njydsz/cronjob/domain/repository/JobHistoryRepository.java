package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.entity.job.JobHistory;
import com.njydsz.cronjob.domain.vo.JobHistoryVO;

/**
 * 任务历史记录 Repository（domain 层契约）。
 *
 * <p>定义任务配置历史版本的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobHistoryVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobHistoryRepository {

  /**
   * 根据任务 ID 查询历史版本列表（按版本号降序）。
   *
   * @param jobId 任务 ID
   * @return 历史版本 VO 列表
   */
  List<JobHistoryVO> findByJobIdOrderByVersionDesc(String jobId);

  /**
   * 根据任务 ID 和版本号查询历史记录。
   *
   * @param jobId 任务 ID
   * @param version 版本号
   * @return 历史记录 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobHistoryVO> findByVersion(String jobId, Integer version);

  /**
   * 清理过期历史记录。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  // ===== 实体方法（JobHistoryServiceImpl 使用） =====

  /** 新增历史记录实体。 */
  int insert(JobHistory history);

  /** 按任务 ID 查询历史版本实体列表（版本号降序）。 */
  List<JobHistory> selectByJobIdOrderByVersionDesc(String jobId);

  /** 按任务 ID 与版本号查询历史记录实体。 */
  JobHistory selectByVersion(String jobId, Integer version);
}
