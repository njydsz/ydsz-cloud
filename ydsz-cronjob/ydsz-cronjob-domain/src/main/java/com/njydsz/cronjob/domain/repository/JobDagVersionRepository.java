package com.njydsz.cronjob.domain.repository;

import java.util.List;
import java.util.Optional;
import com.njydsz.cronjob.domain.vo.JobDagVersionVO;



/**
 * DAG 版本历史 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface JobDagVersionRepository {

  /**
   * 根据 DAG ID 查询版本列表（按版本号降序）。
   *
   * @param dagId DAG ID
   * @param limit 返回条数上限
   * @return 版本列表
   */
  List<JobDagVersionVO> findByVersionDesc(String dagId, int limit);

  /**
   * 查询指定 DAG 的最大版本号。
   *
   * @param dagId DAG 定义 ID
   * @return 最大版本号；无记录时返回 {@code Optional.empty()}
   */
  Optional<Integer> findMaxVersion(String dagId);

  /**
   * 根据 DAG ID 和版本号查询版本记录。
   *
   * @param dagId DAG 定义 ID
   * @param version 版本号
   * @return 匹配的版本记录；不存在时返回 {@code Optional.empty()}
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
