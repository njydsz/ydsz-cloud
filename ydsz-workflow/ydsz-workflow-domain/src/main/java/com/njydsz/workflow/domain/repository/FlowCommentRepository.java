package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowCommentVO;

/**
 * 审批意见仓储接口（domain 层契约）。
 *
 * <p>定义审批意见（ydsz_flow_comment）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作审批意见聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowCommentVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / taskId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowCommentRepository {

  /**
   * 保存审批意见（新增）。
   *
   * @param vo 审批意见 VO
   * @return 保存后的审批意见 VO（含生成的 id 与审计字段）
   */
  FlowCommentVO save(FlowCommentVO vo);

  /**
   * 根据 ID 查询审批意见。
   *
   * @param id 审批意见 ID
   * @return 审批意见 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowCommentVO> findById(String id);

  /**
   * 根据实例 ID 查询审批意见列表。
   *
   * @param instanceId 实例 ID
   * @return 审批意见 VO 列表
   */
  List<FlowCommentVO> findByInstanceId(String instanceId);

  /**
   * 根据任务 ID 查询审批意见列表。
   *
   * @param taskId 任务 ID
   * @return 审批意见 VO 列表
   */
  List<FlowCommentVO> findByTaskId(String taskId);

  /**
   * 根据 ID 删除审批意见。
   *
   * @param id 审批意见 ID
   */
  void deleteById(String id);

  /**
   * 更新审批意见。
   *
   * @param vo 审批意见 VO（含 id）
   * @return 更新后的审批意见 VO
   */
  FlowCommentVO update(FlowCommentVO vo);

  /**
   * 查询实例的根评论列表（一级评论）。
   *
   * <p>返回 {@code parentCommentId = null} 的评论，按创建时间正序排列。
   *
   * @param instanceId 实例 ID
   * @return 根评论 VO 列表
   */
  List<FlowCommentVO> findRootComments(String instanceId);

  /**
   * 查询某条评论的回复列表。
   *
   * <p>返回 {@code parentCommentId = commentId} 的评论，按创建时间正序排列。
   *
   * @param commentId 父评论 ID
   * @return 回复评论 VO 列表
   */
  List<FlowCommentVO> findReplies(String commentId);

  /**
   * 查询实例下全部评论（一级 + 回复），带租户隔离。
   *
   * <p>前端一次性拉取后本地组装树结构，避免 N+1 查询。
   *
   * @param tenantId 租户 ID
   * @param instanceId 实例 ID
   * @return 全部评论 VO 列表
   */
  List<FlowCommentVO> findByInstanceAndTenant(String tenantId, String instanceId);

  /**
   * 查询实例下全部根评论（一级评论），带租户隔离。
   *
   * @param tenantId 租户 ID
   * @param instanceId 实例 ID
   * @return 根评论 VO 列表
   */
  List<FlowCommentVO> findRootCommentsByTenant(String tenantId, String instanceId);
}
