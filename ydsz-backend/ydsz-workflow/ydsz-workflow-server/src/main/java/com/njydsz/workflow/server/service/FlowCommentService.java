package com.njydsz.workflow.server.service;

import java.util.List;

import com.njydsz.workflow.domain.dto.FlowCommentCreateDTO;
import com.njydsz.workflow.domain.entity.FlowComment;

/**
 * P2-2: 流程评论 Service
 *
 * <p>提供审批评论的多级回复能力，对标钉钉/飞书审批评论区。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>发表与回复</b>：一级评论（{@code parentCommentId=null}）/ 多级回复</li>
 *   <li><b>查询能力</b>：实例全部评论（{@link #listByInstance}）/ 一级评论（{@link #listRootComments}）/ 子评论（{@code listChildComments}）</li>
 *   <li><b>删除与编辑</b>：仅评论本人或管理员可删除（{@code deleteById}）</li>
 *   <li><b>通知触发</b>：评论或回复时通过 {@code FlowNotificationService} 通知被回复人</li>
 * </ul>
 *
 * <p><b>与审计日志的区别：</b>
 * <ul>
 *   <li>评论（{@link FlowComment}）：用户视角，可修改可删除</li>
 *   <li>审计日志（{@code FlowAuditLog}）：系统视角，不可修改不可删除</li>
 * </ul>
 *
 * <p><b>事务边界：</b>{@link #addComment} 开启 {@code @Transactional}，
 * 确保「评论写入 + @通知 + 提及人索引」原子性。
 *
 * <p><b>性能优化：</b>
 * <ul>
 *   <li>{@link #listByInstance} 一次性查询全部评论（含回复），由前端本地组装树，避免 N+1</li>
 *   <li>评论分页采用 {@code created_at + parent_comment_id} 复合索引</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.workflow.server.service.impl.FlowCommentServiceImpl 实现类
 * @see FlowAuditLog 流程审计日志
 */
public interface FlowCommentService {

    /**
     * 发表评论或回复。
     *
     * <p>若 {@code dto.parentCommentId} 非空，校验父评论存在且属于同一实例，
     * 然后插入回复记录；否则插入一级评论。
     *
     * @param dto       评论参数
     * @param userId    评论人 ID
     * @param userName  评论人姓名
     * @param tenantId  租户 ID
     * @return 新评论 ID
     */
    String addComment(FlowCommentCreateDTO dto, String userId, String userName, String tenantId);

    /**
     * 查询实例下全部评论（一级 + 回复，按创建时间正序）。
     *
     * <p>前端一次性拉取后本地组装树结构，避免 N+1 查询。
     *
     * @param tenantId   租户 ID
     * @param instanceId 实例 ID
     * @return 全部评论列表
     */
    List<FlowComment> listByInstance(String tenantId, String instanceId);

    /**
     * 查询实例下全部一级评论（按创建时间正序，不含回复）。
     *
     * @param tenantId   租户 ID
     * @param instanceId 实例 ID
     * @return 一级评论列表
     */
    List<FlowComment> listRootComments(String tenantId, String instanceId);

    /**
     * 查询指定父评论下的全部回复（按创建时间正序）。
     *
     * @param parentCommentId 父评论 ID
     * @return 回复列表
     */
    List<FlowComment> listReplies(String parentCommentId);

    /**
     * 删除评论（软删除）。
     *
     * <p>仅评论人本人可删除自己的评论。删除一级评论时，其下回复保留（前端显示"该评论已删除"）。
     *
     * @param commentId 评论 ID
     * @param userId    操作人 ID（校验与评论人一致）
     * @return 是否删除成功（评论不存在或无权限返回 false）
     */
    boolean deleteComment(String commentId, String userId);
}
