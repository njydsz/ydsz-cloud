package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowSkipVO;

/**
 * 节点跳转仓储接口（domain 层契约）。
 *
 * <p>定义节点跳转（ydsz_flow_skip）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作节点跳转聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowSkipVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / fromNodeCode / toNodeCode 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowSkipRepository {

  /**
   * 保存节点跳转（新增）。
   *
   * @param vo 节点跳转 VO
   * @return 保存后的节点跳转 VO（含生成的 id 与审计字段）
   */
  FlowSkipVO save(FlowSkipVO vo);

  /**
   * 根据 ID 查询节点跳转。
   *
   * @param id 节点跳转 ID
   * @return 节点跳转 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowSkipVO> findById(String id);

  /**
   * 根据实例 ID 查询节点跳转列表。
   *
   * @param instanceId 实例 ID
   * @return 节点跳转 VO 列表
   */
  List<FlowSkipVO> findByInstanceId(String instanceId);

  /**
   * 根据 ID 删除节点跳转。
   *
   * @param id 节点跳转 ID
   */
  void deleteById(String id);

  /**
   * 更新节点跳转。
   *
   * @param vo 节点跳转 VO（含 id）
   * @return 更新后的节点跳转 VO
   */
  FlowSkipVO update(FlowSkipVO vo);
}
