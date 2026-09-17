package com.njydsz.agent.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.agent.domain.entity.DagWorkflow;

/**
 * DAG 工作流仓储接口（Repository 契约定义在领域层）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface DagWorkflowRepository {

  /** 新增工作流 */
  boolean insert(DagWorkflow workflow);

  /** 根据 ID 更新工作流 */
  boolean updateById(DagWorkflow workflow);

  /** 根据 ID 查询 */
  Optional<DagWorkflow> findById(String id);

  /** 根据编码查询（业务唯一） */
  Optional<DagWorkflow> findByCode(String workflowCode);

  /** 查询所有未删除的工作流 */
  List<DagWorkflow> findAll();

  /** 根据分类查询 */
  List<DagWorkflow> findByCategory(String category);

  /** 逻辑删除 */
  boolean deleteById(String id);

  /** 编码唯一性校验 */
  boolean existsByCode(String workflowCode);
}
