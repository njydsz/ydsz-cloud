package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;


/**
 * 审批常用语仓储接口（domain 层契约）。
 *
 * <p>定义审批常用语（ydsz_flow_quick_comment）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作常用语聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowQuickCommentVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（userId / tenantId / isSystem 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowQuickCommentRepository {

  /**
   * 保存常用语（新增）。
   *
   * @param vo 常用语 VO
   * @return 保存后的常用语 VO（含生成的 id 与审计字段）
   */
  FlowQuickCommentVO save(FlowQuickCommentVO vo);

  /**
   * 根据 ID 查询常用语。
   *
   * @param id 常用语 ID
   * @return 常用语 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowQuickCommentVO> findById(String id);

  /**
   * 更新常用语。
   *
   * @param vo 常用语 VO（含 id）
   * @return 更新后的常用语 VO
   */
  FlowQuickCommentVO update(FlowQuickCommentVO vo);

  /**
   * 删除常用语（软删除）。
   *
   * @param id 常用语 ID
   */
  void deleteById(String id);

  /**
   * 查询用户自定义的常用语列表（未删除）。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 常用语 VO 列表
   */
  List<FlowQuickCommentVO> findActiveByUser(String userId, String tenantId);

  /**
   * 查询系统预设常用语列表（isSystem=1，未删除）。
   *
   * @param tenantId 租户 ID
   * @return 常用语 VO 列表
   */
  List<FlowQuickCommentVO> findActiveSystemByTenant(String tenantId);
}
