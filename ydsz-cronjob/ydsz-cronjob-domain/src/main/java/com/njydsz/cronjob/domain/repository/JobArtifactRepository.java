package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

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
   */
  List<JobArtifactVO> findByLogId(String logId);

  /**
   * 清理过期产物记录。
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
