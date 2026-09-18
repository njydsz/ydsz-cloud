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

  /**
   * 新增工作流.
   *
   * @param workflow 待新增的工作流实体，不可为空
   * @return 新增成功返回 {@code true}，否则返回 {@code false}
   */
  boolean insert(DagWorkflow workflow);

  /**
   * 根据 ID 更新工作流.
   *
   * @param workflow 待更新的工作流实体，须包含有效 ID，不可为空
   * @return 更新成功返回 {@code true}，否则返回 {@code false}
   */
  boolean updateById(DagWorkflow workflow);

  /**
   * 根据 ID 查询.
   *
   * @param id 工作流唯一标识，不可为空
   * @return 匹配的工作流实体，未找到时返回 {@link Optional#empty()}
   */
  Optional<DagWorkflow> findById(String id);

  /**
   * 根据编码查询（业务唯一）.
   *
   * @param workflowCode 工作流业务编码，不可为空
   * @return 匹配的工作流实体，未找到时返回 {@link Optional#empty()}
   */
  Optional<DagWorkflow> findByCode(String workflowCode);

  /**
   * 查询所有未删除的工作流.
   *
   * @return 工作流实体列表，无数据时返回空列表
   */
  List<DagWorkflow> findAll();

  /**
   * 根据分类查询.
   *
   * @param category 分类标签，可空时返回全部
   * @return 匹配分类的工作流实体列表
   */
  List<DagWorkflow> findByCategory(String category);

  /**
   * 逻辑删除.
   *
   * @param id 工作流唯一标识，不可为空
   * @return 删除成功返回 {@code true}，否则返回 {@code false}
   */
  boolean deleteById(String id);

  /**
   * 编码唯一性校验.
   *
   * @param workflowCode 待校验的工作流业务编码，不可为空
   * @return 已存在返回 {@code true}，否则返回 {@code false}
   */
  boolean existsByCode(String workflowCode);
}
