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
   * @param logId 参数说明
   * @return 返回值说明
   */
  List<JobArtifactVO> findByLogId(String logId);

  /**
   * 按 ID 查询产物。
   *
   * @param id 参数说明
   * @return 返回值说明
   */
  Optional<JobArtifactVO> findById(String id);

  /**
   * 清理过期产物记录。
   *
   * @param before 参数说明
   * @param limit 参数说明
   * @return 返回值说明
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
