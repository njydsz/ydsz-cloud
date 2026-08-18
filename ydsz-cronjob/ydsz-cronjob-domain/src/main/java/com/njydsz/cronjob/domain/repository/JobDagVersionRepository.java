package com.njydsz.cronjob.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobDagVersionVO;

/**
 * DAG 版本历史 Repository（domain 层契约）。
 *
 * <p>定义 DAG 工作流版本历史的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobDagVersionVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagVersionRepository {

  /**
   * 根据 DAG ID 查询版本列表（按版本号降序）。
   *
   * @param dagId DAG ID
   * @return 版本 VO 列表
   */
  List<JobDagVersionVO> findByVersionDesc(String dagId);

  /**
   * 查询指定 DAG 的最大版本号。
   *
   * @param dagId DAG ID
   * @return 最大版本号；无记录返回 {@code Optional.empty()}
   */
  Optional<Integer> findMaxVersion(String dagId);

  /**
   * 根据 DAG ID 和版本号查询版本记录。
   *
   * @param dagId DAG ID
   * @param version 版本号
   * @return 版本记录 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobDagVersionVO> findByVersion(String dagId, Integer version);
}
