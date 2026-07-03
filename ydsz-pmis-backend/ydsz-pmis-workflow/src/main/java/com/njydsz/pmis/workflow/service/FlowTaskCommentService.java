package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.entity.FlowTaskCommentDO;

import java.util.List;

/**
 * 任务评论服务
 *
 * <p>提供工作流任务下的独立沟通评论能力，区别于任务操作（通过/驳回）时附带的审批意见。
 * 支持评论（COMMENT）、提问（QUESTION）、回复（REPLY）三种类型，并通过 parentId 支持楼中楼回复。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowTaskCommentService {

    /**
     * 添加评论
     *
     * @param instanceId 流程实例 ID
     * @param taskId     任务 ID
     * @param nodeCode   节点编码（可空）
     * @param userId     评论人 ID
     * @param userName   评论人姓名（可空）
     * @param content    评论内容
     * @param type       评论类型：COMMENT / QUESTION / REPLY（可空，默认 COMMENT）
     * @param parentId   父评论 ID（可空，用于楼中楼回复）
     * @return 新建的评论记录
     */
    FlowTaskCommentDO addComment(Long instanceId, Long taskId, String nodeCode,
                                 Long userId, String userName, String content,
                                 String type, Long parentId);

    /**
     * 按任务 ID 查询评论列表（按创建时间正序）
     *
     * @param taskId 任务 ID
     * @return 评论列表
     */
    List<FlowTaskCommentDO> listByTaskId(Long taskId);

    /**
     * 按流程实例 ID 查询评论列表（按创建时间正序）
     *
     * @param instanceId 流程实例 ID
     * @return 评论列表
     */
    List<FlowTaskCommentDO> listByInstanceId(Long instanceId);

    /**
     * 删除评论（仅评论发起人可删除）
     *
     * @param commentId 评论 ID
     * @param userId    操作人 ID（用于归属校验）
     * @return 是否删除成功
     */
    boolean deleteComment(Long commentId, Long userId);
}
