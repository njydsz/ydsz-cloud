package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobArtifactVO;

/**
 * 任务产物 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobArtifactRepository {

  /**
   * 根据日志 ID 查询产物列表。
   *
   * @param logId 执行日志 ID
   * @return 该日志关联的产物 VO 列表，无记录时返回空列表
   */
  List<JobArtifactVO> findByLogId(String logId);

  /**
   * 按 ID 查询产物。
   *
   * @param id 产物 ID
   * @return 产物 VO；不存在时返回 {@code Optional.empty()}
   */
  Optional<JobArtifactVO> findById(String id);

  /**
   * 清理过期产物记录。
   *
   * @param before 过期时间分界点（删除此时间之前过期的记录）
   * @param limit 单批最多删除条数
   * @return 实际删除的产物记录条数
   */
  int cleanExpired(LocalDateTime before, int limit);

  /**
   * 新增产物。
   *
   * @param vo 产物 VO
   * @return 新记录 ID
   */
  String insert(JobArtifactVO vo);
}
