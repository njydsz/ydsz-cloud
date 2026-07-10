package com.njydsz.pmis.workflow.service.impl.notification;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.notification.FlowCommentCreateDTO;
import com.njydsz.pmis.workflow.engine.FlowSensitiveMasker;
import com.njydsz.pmis.workflow.entity.notification.FlowCommentDO;
import com.njydsz.pmis.workflow.mapper.notification.FlowCommentMapper;
import com.njydsz.pmis.workflow.service.notification.FlowCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * P2-2: 流程评论 Service 实现
 *
 * <p>审批评论多级回复实现。独立于审计日志（{@code FlowTaskSupport.audit}），
 * 评论是讨论（可回复、可删除），审计日志是操作轨迹（不可变）。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCommentServiceImpl implements FlowCommentService {

    /** 评论记录 Mapper，负责 pmis_flow_comment 表的增删改查及多级回复查询 */
    private final FlowCommentMapper commentMapper;
    /** P0-1: 敏感字段脱敏器，对评论内容中的手机号/身份证等敏感信息做实时脱敏 */
    private final FlowSensitiveMasker sensitiveMasker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addComment(FlowCommentCreateDTO dto, String userId, String userName, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_a7b8c9d0");
        }
        // 回复场景：校验父评论存在且属于同一实例
        if (StringUtils.hasText(dto.getParentCommentId())) {
            FlowCommentDO parent = commentMapper.selectById(dto.getParentCommentId());
            if (parent == null || parent.getDeleted() == 1) {
                throw new BizException(BizErrorCode.NOT_FOUND,
                        "error.workflow.msg_f2a3b4c5", dto.getParentCommentId());
            }
            if (!parent.getInstanceId().equals(dto.getInstanceId())) {
                throw new BizException(BizErrorCode.BAD_REQUEST,
                        "error.workflow.msg_a3b4c5d6");
            }
        }

        FlowCommentDO comment = new FlowCommentDO();
        comment.setTenantId(tenantId != null ? tenantId : "1");
        comment.setInstanceId(dto.getInstanceId());
        comment.setTaskId(dto.getTaskId());
        comment.setNodeCode(dto.getNodeCode());
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setContent(sensitiveMasker.mask(dto.getContent()));
        comment.setParentCommentId(dto.getParentCommentId());
        comment.setReplyToUserId(dto.getReplyToUserId());
        comment.setReplyToUserName(dto.getReplyToUserName());
        // 评论类型默认 COMMENT（吸收 task_comment 功能后新增字段）
        comment.setType("COMMENT");
        commentMapper.insert(comment);
        log.info("[FlowComment] 新增评论: commentId={} instanceId={} userId={} isReply={}",
                comment.getId(), dto.getInstanceId(), userId,
                StringUtils.hasText(dto.getParentCommentId()));
        return comment.getId();
    }

    @Override
    public List<FlowCommentDO> listByInstance(String tenantId, String instanceId) {
        return commentMapper.listByInstance(tenantId, instanceId);
    }

    @Override
    public List<FlowCommentDO> listRootComments(String tenantId, String instanceId) {
        return commentMapper.listRootComments(tenantId, instanceId);
    }

    @Override
    public List<FlowCommentDO> listReplies(String parentCommentId) {
        return commentMapper.listReplies(parentCommentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteComment(String commentId, String userId) {
        FlowCommentDO comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            return false;
        }
        // 仅评论人本人可删除自己的评论
        if (!comment.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.FORBIDDEN, "error.workflow.msg_b4c5d6e7");
        }
        comment.setDeleted(1);
        commentMapper.updateById(comment);
        log.info("[FlowComment] 删除评论: commentId={} userId={}", commentId, userId);
        return true;
    }
}
