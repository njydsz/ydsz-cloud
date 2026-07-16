package com.njydsz.workflow.server.service.impl.notification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowCommentCreateDTO;
import com.njydsz.workflow.domain.entity.FlowCommentDO;
import com.njydsz.workflow.infra.mapper.FlowCommentMapper;
import com.njydsz.workflow.server.engine.FlowSensitiveMasker;
import com.njydsz.workflow.server.service.FlowCommentService;
import com.njydsz.workflow.server.service.FlowNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-2: 流程评论 Service 实现
 *
 * <p>审批评论多级回复实现。独立于审计日志（{@code FlowTaskSupport.audit}），
 * 评论是讨论（可回复、可删除），审计日志是操作轨迹（不可变）。
 *
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCommentServiceImpl implements FlowCommentService {

    /** 评论记录 Mapper，负责 ydsz_flow_comment 表的增删改查及多级回复查询 */
    private final FlowCommentMapper commentMapper;
    /** P0-1: 敏感字段脱敏器，对评论内容中的手机号/身份证等敏感信息做实时脱敏 */
    private final FlowSensitiveMasker sensitiveMasker;
    /** P2-1: 通知服务（@Lazy 避免循环依赖） */
    @Lazy
    private final FlowNotificationService notificationService;

    /** P2-1: @提及正则，匹配 @{userId} 或 @userId 格式 */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\{([a-zA-Z0-9_-]+)\\}|@([a-zA-Z0-9_-]+)");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addComment(FlowCommentCreateDTO dto, String userId, String userName, String tenantId) {
        if (!StringUtils.hasText(userId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_a7b8c9d0");
        }
        // 回复场景：校验父评论存在且属于同一实例
        if (StringUtils.hasText(dto.getParentCommentId())) {
            FlowCommentDO parent = commentMapper.selectById(dto.getParentCommentId());
            if (parent == null || parent.getDeleted() == 1) {
                throw new SysException(BaseResultCode.NOT_FOUND,
                        "error.workflow.msg_f2a3b4c5", dto.getParentCommentId());
            }
            if (!parent.getInstanceId().equals(dto.getInstanceId())) {
                throw new SysException(BaseResultCode.BAD_REQUEST,
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

        // P2-1: 解析 @提及并发送通知
        try {
            List<String> mentionedUserIds = parseMentions(comment.getContent());
            if (!mentionedUserIds.isEmpty()) {
                String title = "审批评论提及通知";
                String content = userName + " 在流程评论中提及了您: " + comment.getContent();
                for (String mentionedUserId : mentionedUserIds) {
                    // 不通知自己
                    if (!mentionedUserId.equals(userId)) {
                        notificationService.send("WORKFLOW", mentionedUserId, title, content,
                                Map.of("instanceId", dto.getInstanceId(),
                                        "commentId", comment.getId(),
                                        "type", "MENTION"));
                    }
                }
                log.info("[FlowComment] P2-1 @提及通知: commentId={} mentioned={}",
                        comment.getId(), mentionedUserIds);
            }
        } catch (Exception e) {
            // 通知失败不影响评论发布
            log.warn("[FlowComment] P2-1 @提及通知失败: commentId={} err={}",
                    comment.getId(), e.getMessage());
        }

        // P2-1: 回复通知（回复某条评论时通知被回复人）
        if (StringUtils.hasText(dto.getReplyToUserId())
                && !dto.getReplyToUserId().equals(userId)) {
            try {
                String replyTitle = "审批评论回复通知";
                String replyContent = userName + " 回复了您的评论: " + comment.getContent();
                notificationService.send("WORKFLOW", dto.getReplyToUserId(),
                        replyTitle, replyContent,
                        Map.of("instanceId", dto.getInstanceId(),
                                "commentId", comment.getId(),
                                "type", "REPLY"));
            } catch (Exception e) {
                log.warn("[FlowComment] P2-1 回复通知失败: commentId={} err={}",
                        comment.getId(), e.getMessage());
            }
        }

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
            throw new SysException(BaseResultCode.FORBIDDEN, "error.workflow.msg_b4c5d6e7");
        }
        comment.setDeleted(1);
        commentMapper.updateById(comment);
        log.info("[FlowComment] 删除评论: commentId={} userId={}", commentId, userId);
        return true;
    }

    // ==================== P2-1: @提及解析 ====================

    /**
     * 解析评论内容中的 @提及，提取被提及的用户 ID 列表。
     *
     * <p>支持两种格式：
     * <ul>
     *   <li>{@code @{userId}} — 大括号包裹格式（推荐，避免歧义）</li>
     *   <li>{@code @userId} — 简单格式</li>
     * </ul>
     *
     * @param content 评论内容
     * @return 去重后的用户 ID 列表（有序）
     */
    private List<String> parseMentions(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        Set<String> userIds = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            // group(1) 为 @{userId} 格式，group(2) 为 @userId 格式
            String userId = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (userId != null && !userId.isBlank()) {
                userIds.add(userId);
            }
        }
        return new ArrayList<>(userIds);
    }
}
