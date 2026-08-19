package com.njydsz.cronjob.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobDagVersionVO;

/**
 * DAG 版本历史 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagVersionRepository {

  /**
   * 根据 DAG ID 查询版本列表（按版本号降序）。
   */
  List<JobDagVersionVO> findByVersionDesc(String dagId);

  /**
   * 查询指定 DAG 的最大版本号。
   */
  Optional<Integer> findMaxVersion(String dagId);

  /**
   * 根据 DAG ID 和版本号查询版本记录。
   */
  Optional<JobDagVersionVO> findByVersion(String dagId, Integer version);

  /**
   * 新增版本记录。
   *
   * @param vo 版本 VO
   * @return 新记录 ID
   */
  String insert(JobDagVersionVO vo);
}
