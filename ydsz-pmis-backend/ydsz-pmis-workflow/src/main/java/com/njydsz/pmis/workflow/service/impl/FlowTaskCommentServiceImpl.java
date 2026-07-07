package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.entity.FlowTaskCommentDO;
import com.njydsz.pmis.workflow.mapper.FlowTaskCommentMapper;
import com.njydsz.pmis.workflow.service.FlowTaskCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务评论服务实现
 *
 * <p>核心能力：
 * <ul>
 *   <li>{@link #addComment} — 在任务下新增沟通评论（COMMENT/QUESTION/REPLY），支持楼中楼回复</li>
 *   <li>{@link #listByTaskId} — 按任务维度查询评论列表（按创建时间正序）</li>
 *   <li>{@link #listByInstanceId} — 按实例维度查询评论列表（按创建时间正序）</li>
 *   <li>{@link #deleteComment} — 删除评论，仅评论发起人可删除</li>
 * </ul>
 *
 * <p>所有方法均防御性编码：空值检查 + try-catch，保证不拖垮主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskCommentServiceImpl implements FlowTaskCommentService {

    private final FlowTaskCommentMapper taskCommentMapper;

    // ============================== 新增评论 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowTaskCommentDO addComment(String instanceId, Long taskId, String nodeCode,
                                       String userId, String userName, String content,
                                       String type, Long parentId) {
        FlowTaskCommentDO comment = new FlowTaskCommentDO();
        LocalDateTime now = LocalDateTime.now();
        comment.setTenantId(SecurityContext.getTenantIdOrDefault("1"));
        comment.setInstanceId(instanceId);
        comment.setTaskId(taskId);
        comment.setNodeCode(nodeCode);
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setContent(content);
        // 类型缺省为 COMMENT
        comment.setType(StringUtils.hasText(type) ? type : "COMMENT");
        comment.setParentId(parentId);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);

        taskCommentMapper.insert(comment);
        log.info("[FlowTaskComment] 新增评论: instanceId={} taskId={} userId={} type={} commentId={}",
                instanceId, taskId, userId, comment.getType(), comment.getId());
        return comment;
    }

    // ============================== 查询 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<FlowTaskCommentDO> listByTaskId(Long taskId) {
        try {
            if (taskId == null) {
                return List.of();
            }
            LambdaQueryWrapper<FlowTaskCommentDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FlowTaskCommentDO::getTaskId, taskId)
                    .orderByAsc(FlowTaskCommentDO::getCreatedAt);
            return taskCommentMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowTaskComment] 按任务查询评论异常: taskId={} err={}", taskId, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowTaskCommentDO> listByInstanceId(String instanceId) {
        try {
            if (instanceId == null) {
                return List.of();
            }
            LambdaQueryWrapper<FlowTaskCommentDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FlowTaskCommentDO::getInstanceId, instanceId)
                    .orderByAsc(FlowTaskCommentDO::getCreatedAt);
            return taskCommentMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("[FlowTaskComment] 按实例查询评论异常: instanceId={} err={}",
                    instanceId, e.getMessage(), e);
            return List.of();
        }
    }

    // ============================== 删除 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteComment(Long commentId, String userId) {
        try {
            if (commentId == null) {
                return false;
            }
            FlowTaskCommentDO existing = taskCommentMapper.selectById(commentId);
            if (existing == null) {
                log.warn("[FlowTaskComment] 删除评论失败，评论不存在: commentId={}", commentId);
                return false;
            }
            // 归属校验：仅评论发起人可删除
            if (userId == null || !userId.equals(existing.getUserId())) {
                log.warn("[FlowTaskComment] 删除评论失败，无权限: commentId={} userId={} ownerId={}",
                        commentId, userId, existing.getUserId());
                return false;
            }
            int rows = taskCommentMapper.deleteById(commentId);
            log.info("[FlowTaskComment] 删除评论: commentId={} userId={} affected={}",
                    commentId, userId, rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("[FlowTaskComment] 删除评论异常: commentId={} userId={} err={}",
                    commentId, userId, e.getMessage(), e);
            return false;
        }
    }
}
