package com.njydsz.workflow.server.service;

import java.util.List;

import com.njydsz.workflow.domain.dto.FlowCommentCreateDTO;
import com.njydsz.workflow.domain.dto.FlowQuickCommentDTO;
import com.njydsz.workflow.infra.entity.FlowCommentDO;
import com.njydsz.workflow.infra.entity.FlowQuickCommentDO;

/**
 * P2-2: 流程评论 Service（含常用语能力）
 *
 * <p>提供审批评论的多级回复能力，对标钉钉/飞书审批评论区。
 * 同时集成审批常用语（快捷回复模板）的 CRUD 与使用统计能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>发表与回复</b>：一级评论（{@code parentCommentId=null}）/ 多级回复
 *   <li><b>查询能力</b>：实例全部评论（{@link #listByInstance}）/ 一级评论（{@link #listRootComments}）/ 子评论（{@code
 *       listChildComments}）
 *   <li><b>删除与编辑</b>：仅评论本人或管理员可删除（{@code deleteComment}）
 *   <li><b>通知触发</b>：评论或回复时通过 {@code FlowNotificationService} 通知被回复人
 *   <li><b>常用语管理</b>：用户自定义常用语的增删改查、使用次数统计（{@link #listQuickComments} / {@link #createQuickComment}
 *       / {@link #updateQuickComment} / {@link #deleteQuickComment} / {@link #incrementQuickCommentUseCount}）
 * </ul>
 *
 * <p><b>与审计日志的区别：</b>
 *
 * <ul>
 *   <li>评论（{@link FlowCommentDO}）：用户视角，可修改可删除
 *   <li>审计日志（{@code FlowAuditLogDO}）：系统视角，不可修改不可删除
 * </ul>
 *
 * <p><b>事务边界：</b>{@link #addComment} 开启 {@code @Transactional}， 确保「评论写入 + @通知 + 提及人索引」原子性。
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>{@link #listByInstance} 一次性查询全部评论（含回复），由前端本地组装树，避免 N+1
 *   <li>评论分页采用 {@code created_at + parent_comment_id} 复合索引
 *   <li>常用语数据量小（用户级百级别），无需分页
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.FlowCommentServiceImpl 实现类
 * @see FlowAuditLogDO 流程审计日志
 */
public interface FlowCommentService {

  /**
   * 发表评论或回复。
   *
   * <p>若 {@code dto.parentCommentId} 非空，校验父评论存在且属于同一实例， 然后插入回复记录；否则插入一级评论。
   *
   * @param dto 评论参数
   * @param userId 评论人 ID
   * @param userName 评论人姓名
   * @param tenantId 租户 ID
   * @return 新评论 ID
   */
  String addComment(FlowCommentCreateDTO dto, String userId, String userName, String tenantId);

  /**
   * 查询实例下全部评论（一级 + 回复，按创建时间正序）。
   *
   * <p>前端一次性拉取后本地组装树结构，避免 N+1 查询。
   *
   * @param tenantId 租户 ID
   * @param instanceId 实例 ID
   * @return 全部评论列表
   */
  List<FlowCommentDO> listByInstance(String tenantId, String instanceId);

  /**
   * 查询实例下全部一级评论（按创建时间正序，不含回复）。
   *
   * @param tenantId 租户 ID
   * @param instanceId 实例 ID
   * @return 一级评论列表
   */
  List<FlowCommentDO> listRootComments(String tenantId, String instanceId);

  /**
   * 查询指定父评论下的全部回复（按创建时间正序）。
   *
   * @param parentCommentId 父评论 ID
   * @return 回复列表
   */
  List<FlowCommentDO> listReplies(String parentCommentId);

  /**
   * 删除评论（软删除）。
   *
   * <p>仅评论人本人可删除自己的评论。删除一级评论时，其下回复保留（前端显示"该评论已删除"）。
   *
   * @param commentId 评论 ID
   * @param userId 操作人 ID（校验与评论人一致）
   * @return 是否删除成功（评论不存在或无权限返回 false）
   */
  boolean deleteComment(String commentId, String userId);

  // ==================== P2-1: 审批常用语能力（由 FlowQuickCommentService 合并） ====================

  /**
   * 查询用户的常用语列表（含系统预设 + 用户自定义）。
   *
   * <p>合并查询：先查用户自定义常用语，再追加系统预设，最终按 sortNum 升序、useCount 降序两级排序。
   *
   * @param userId 用户 ID（不可空，为空返回空列表）
   * @param tenantId 租户 ID
   * @return 常用语列表（已合并 + 已排序），无数据返回空列表
   */
  List<FlowQuickCommentDO> listQuickComments(String userId, String tenantId);

  /**
   * 创建用户自定义常用语。
   *
   * <p>仅创建用户自定义常用语（isSystem=0），useCount 初始为 0。
   *
   * @param dto 常用语 DTO（含 content/commentType/sortNum）
   * @param userId 创建人 ID（不可空）
   * @param tenantId 租户 ID
   * @return 新常用语 ID
   */
  String createQuickComment(FlowQuickCommentDTO dto, String userId, String tenantId);

  /**
   * 更新常用语（仅创建者本人可更新，系统预设不可更新）。
   *
   * @param dto 常用语 DTO（id 必传）
   * @param userId 操作人 ID（必须与创建者一致）
   */
  void updateQuickComment(FlowQuickCommentDTO dto, String userId);

  /**
   * 删除常用语（软删除）。
   *
   * <p>系统预设（isSystem=1）不可删除，仅创建者本人可删除。
   *
   * @param id 常用语 ID
   * @param userId 操作人 ID
   */
  void deleteQuickComment(String id, String userId);

  /**
   * 增加常用语使用次数（审批时调用）。
   *
   * <p>异常被 try-catch 吞掉记 WARN，不传播异常——使用统计失败不应阻塞评论发布主流程。
   *
   * @param id 常用语 ID
   */
  void incrementQuickCommentUseCount(String id);
}
