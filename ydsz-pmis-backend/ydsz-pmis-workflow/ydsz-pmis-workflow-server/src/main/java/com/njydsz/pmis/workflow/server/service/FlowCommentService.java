package com.njydsz.pmis.workflow.server.service;

import com.njydsz.pmis.workflow.domain.dto.FlowCommentCreateDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowCommentDO;

import java.util.List;

/**
 * P2-2: 流程评论 Service
 *
 * <p>审批评论多级回复能力。对标钉钉/飞书审批评论区。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
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
    List<FlowCommentDO> listByInstance(String tenantId, String instanceId);

    /**
     * 查询实例下全部一级评论（按创建时间正序，不含回复）。
     *
     * @param tenantId   租户 ID
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
     * @param userId    操作人 ID（校验与评论人一致）
     * @return 是否删除成功（评论不存在或无权限返回 false）
     */
    boolean deleteComment(String commentId, String userId);
}
