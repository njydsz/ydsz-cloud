package com.njydsz.agent.domain.service;

import java.util.List;

import com.njydsz.agent.domain.dto.DagWorkflowDTO;
import com.njydsz.agent.domain.entity.DagWorkflow;
import com.njydsz.agent.domain.vo.DagWorkflowVO;

/**
 * DAG 工作流业务服务接口。
 *
 * <p>定义工作流 CRUD 的业务操作契约，由 server 层实现，
 * Controller 通过本接口访问业务能力（DDD 分层：web → server → domain）。
 *
 * <p>查询方法返回 {@link DagWorkflowVO}，禁止将 Entity 泄露到 Web 层（DDD-007）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface DagWorkflowService {

  /**
   * 保存工作流（新建 / 更新）。
   *
   * <p>workflowCode 为空则新建（自动生成编码），存在则更新。
   *
   * @param dto 工作流数据（必填字段：workflowName / dslContent）
   * @return 保存后的工作流 ID
   * @throws com.njydsz.common.exception.custom.BusinessException 更新时工作流编码不存在
   */
  String save(DagWorkflowDTO dto);

  /**
   * 根据编码查询工作流。
   *
   * @param code 工作流业务编码
   * @return 工作流视图对象，不存在时返回 null
   */
  DagWorkflowVO getByCode(String code);

  /**
   * 查询工作流列表。
   *
   * @param category 分类筛选（可选，传 null / 空字符串则返回全量）
   * @return 工作流视图对象列表，无数据时返回空列表
   */
  List<DagWorkflowVO> listByCategory(String category);

  /**
   * 根据 ID 查询工作流。
   *
   * @param id 工作流唯一标识
   * @return 工作流视图对象，不存在时返回 null
   */
  DagWorkflowVO getById(String id);

  /**
   * 逻辑删除工作流。
   *
   * @param code 工作流业务编码
   * @return 删除成功返回 true
   * @throws com.njydsz.common.exception.custom.BusinessException 工作流编码不存在
   */
  boolean deleteByCode(String code);
}
