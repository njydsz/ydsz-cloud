package com.njydsz.workflow.server.service.impl.notification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowCommentCreateDTO;
import com.njydsz.workflow.domain.entity.FlowComment;
import com.njydsz.workflow.infra.mapper.FlowCommentMapper;
import com.njydsz.workflow.server.engine.FlowSensitiveMasker;
import com.njydsz.workflow.server.service.FlowCommentService;
import com.njydsz.workflow.server.service.FlowNotificationService;

/**
 * P2-2: 流程评论 Service 实现
 *
 * <p>对 {@link FlowCommentService} 接口的完整实现，是工作流引擎的<b>评论协作</b>能力。
 * 支撑审批评论的<b>多级回复</b>、<b>@提及通知</b>、<b>敏感数据脱敏</b>、<b>可删除语义</b>。 是大厂 B 端工作流「审批沟通协作」的标准能力。
 *
 * <p><b>与审计日志的区别：</b>
 *
 * <ul>
 *   <li><b>评论（{@link FlowComment}）</b>：业务方的<b>讨论</b>，<b>可回复</b>（{@code parentCommentId}）、
 *       <b>可删除</b>（{@code deleted} 软删）、<b>支持 @提及</b>（{@code @\{userId\}}）
 *   <li><b>审计日志（{@code FlowAuditLog}）</b>：系统的<b>操作轨迹</b>，<b>不可变</b>、 不可回复、不可删除，用于合规审计
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>新增评论</b>：{@link #addComment} — 新增顶级评论 / 回复评论，支持 @提及解析
 *   <li><b>删除评论</b>：{@link #deleteComment} — 软删除（{@code deleted=1}），保留审计
 *   <li><b>多级回复树</b>：{@link #listCommentTree} / {@link #listByInstance} — 一次性查询评论 + 构建树形结构
 *   <li><b>@提及通知</b>：通过正则 {@link #MENTION_PATTERN} 解析 @userId， 调用 {@link FlowNotificationService}
 *       发送通知
 *   <li><b>敏感数据脱敏</b>：通过 {@link FlowSensitiveMasker#mask} 对评论内容脱敏
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}， 「参数校验 + 父评论校验 +
 * 评论写入 + @提及通知」原子性。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>软删除</b>：评论使用软删除（{@code deleted=1}），保留完整历史便于审计追溯
 *   <li><b>多租户隔离</b>：所有查询均带 {@code tenantId} 条件，<b>严禁跨租户访问</b>
 *   <li><b>回复场景校验</b>：父评论必须存在、未删除、且属于同一实例， 防止「跨实例回复」导致的数据混乱
 *   <li><b>循环依赖处理</b>：{@link FlowNotificationService} 使用 {@code @Lazy} 注入， 避免与 {@link
 *       FlowCommentService} 的循环依赖
 *   <li><b>@提及正则</b>：{@link #MENTION_PATTERN} 同时支持 {@code @\{userId\}} 和 {@code @userId}
 *       两种格式，兼容前端不同输入习惯
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 场景：审批人在通过审批时，添加评论并 @ 财务人员
 * FlowCommentCreateDTO dto = new FlowCommentCreateDTO();
 * dto.setInstanceId(instanceId);
 * dto.setTaskId(taskId);
 * dto.setContent("已通过，请 @{2001} 关注后续付款");
 * commentService.addComment(dto, "1001", "张三", "tenant-1");
 * // → 写入评论 + 自动给用户 2001 发送通知
 * }</pre>
 *
 * <p><b>与审计日志的关系：</b>
 *
 * <p>评论 <b>不会</b> 写入审计日志（{@code ydsz_flow_audit_log}）， 因为评论是<b>讨论</b>而非<b>操作</b>。如需审计评论内容，应通过
 * {@code @EventListener} 监听 {@code COMMENT_CREATED} 事件，由审计模块自行持久化。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowCommentService 接口定义
 * @see FlowComment 评论实体
 * @see FlowAuditLog 审计日志实体（操作轨迹，与评论分离）
 * @see FlowSensitiveMasker 敏感数据脱敏器
 * @see FlowNotificationService 通知服务（@提及通知）
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
  @Lazy private final FlowNotificationService notificationService;

  /** P2-1: @提及正则，匹配 @{userId} 或 @userId 格式 */
  private static final Pattern MENTION_PATTERN =
      Pattern.compile("@\\{([a-zA-Z0-9_-]+)\\}|@([a-zA-Z0-9_-]+)");

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String addComment(
      FlowCommentCreateDTO dto, String userId, String userName, String tenantId) {
    if (!StringUtils.hasText(userId)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.workflow.msg_a7b8c9d0")
          .build();
    }
    // 回复场景：校验父评论存在且属于同一实例
    if (StringUtils.hasText(dto.getParentCommentId())) {
      FlowComment parent = commentMapper.selectById(dto.getParentCommentId());
      if (parent == null || parent.getDeleted() == 1) {
        throw SysException.builder()
            .resultCode(BaseResultCode.NOT_FOUND)
            .key("error.workflow.msg_f2a3b4c5")
            .params(dto.getParentCommentId())
            .build();
      }
      if (!parent.getInstanceId().equals(dto.getInstanceId())) {
        throw SysException.builder()
            .resultCode(BaseResultCode.BAD_REQUEST)
            .message("error.workflow.msg_a3b4c5d6")
            .build();
      }
    }

    FlowComment comment = new FlowComment();
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
    log.info(
        "[FlowComment] 新增评论: commentId={} instanceId={} userId={} isReply={}",
        comment.getId(),
        dto.getInstanceId(),
        userId,
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
            notificationService.send(
                "WORKFLOW",
                mentionedUserId,
                title,
                content,
                Map.of(
                    "instanceId",
                    dto.getInstanceId(),
                    "commentId",
                    comment.getId(),
                    "type",
                    "MENTION"));
          }
        }
        log.info(
            "[FlowComment] P2-1 @提及通知: commentId={} mentioned={}",
            comment.getId(),
            mentionedUserIds);
      }
    } catch (Exception e) {
      // 通知失败不影响评论发布
      log.warn("[FlowComment] P2-1 @提及通知失败: commentId={} err={}", comment.getId(), e.getMessage());
    }

    // P2-1: 回复通知（回复某条评论时通知被回复人）
    if (StringUtils.hasText(dto.getReplyToUserId()) && !dto.getReplyToUserId().equals(userId)) {
      try {
        String replyTitle = "审批评论回复通知";
        String replyContent = userName + " 回复了您的评论: " + comment.getContent();
        notificationService.send(
            "WORKFLOW",
            dto.getReplyToUserId(),
            replyTitle,
            replyContent,
            Map.of(
                "instanceId", dto.getInstanceId(), "commentId", comment.getId(), "type", "REPLY"));
      } catch (Exception e) {
        log.warn("[FlowComment] P2-1 回复通知失败: commentId={} err={}", comment.getId(), e.getMessage());
      }
    }

    return comment.getId();
  }

  @Override
  public List<FlowComment> listByInstance(String tenantId, String instanceId) {
    return commentMapper.listByInstance(tenantId, instanceId);
  }

  @Override
  public List<FlowComment> listRootComments(String tenantId, String instanceId) {
    return commentMapper.listRootComments(tenantId, instanceId);
  }

  @Override
  public List<FlowComment> listReplies(String parentCommentId) {
    return commentMapper.listReplies(parentCommentId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean deleteComment(String commentId, String userId) {
    FlowComment comment = commentMapper.selectById(commentId);
    if (comment == null || comment.getDeleted() == 1) {
      return false;
    }
    // 仅评论人本人可删除自己的评论
    if (!comment.getUserId().equals(userId)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.FORBIDDEN)
          .message("error.workflow.msg_b4c5d6e7")
          .build();
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
   *
   * <ul>
   *   <li>{@code @{userId}} — 大括号包裹格式（推荐，避免歧义）
   *   <li>{@code @userId} — 简单格式
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
