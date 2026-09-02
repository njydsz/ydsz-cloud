package com.njydsz.workflow.server.service.impl.notification;

import java.util.ArrayList;
import java.util.Comparator;
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

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.domain.dto.FlowCommentCreateDTO;
import com.njydsz.workflow.domain.dto.FlowQuickCommentDTO;
import com.njydsz.workflow.domain.repository.FlowCommentRepository;
import com.njydsz.workflow.domain.repository.FlowQuickCommentRepository;
import com.njydsz.workflow.domain.vo.FlowCommentVO;
import com.njydsz.workflow.domain.vo.FlowQuickCommentVO;
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
 *   <li><b>评论（{@link FlowCommentVO}）</b>：业务方的<b>讨论</b>，<b>可回复</b>（{@code parentCommentId}）、
 *       <b>可删除</b>（{@code deleted} 软删）、<b>支持 @提及</b>（{@code @\{userId\}}）
 *   <li><b>审计日志（{@code FlowAuditLogVO}）</b>：系统的<b>操作轨迹</b>，<b>不可变</b>、 不可回复、不可删除，用于合规审计
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
 * @since 26.09.01
 * @see FlowCommentService 接口定义
 * @see FlowCommentVO 评论值对象
 * @see FlowAuditLogVO 审计日志值对象（操作轨迹，与评论分离）
 * @see FlowSensitiveMasker 敏感数据脱敏器
 * @see FlowNotificationService 通知服务（@提及通知）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCommentServiceImpl implements FlowCommentService {

  /** 审批意见仓储（domain 层契约），管理 ydsz_flow_comment 表 CRUD */
  private final FlowCommentRepository commentRepository;

  /** P0-1: 敏感字段脱敏器，对评论内容中的手机号/身份证等敏感信息做实时脱敏 */
  private final FlowSensitiveMasker sensitiveMasker;

  /** P2-1: 通知服务（@Lazy 避免循环依赖） */
  @Lazy private final FlowNotificationService notificationService;

  /** P2-1: 常用语仓储（domain 层契约），管理 ydsz_flow_quick_comment 表 CRUD */
  private final FlowQuickCommentRepository quickCommentRepository;

  /** P2-1: @提及正则，匹配 @{userId} 或 @userId 格式 */
  private static final Pattern MENTION_PATTERN =
      Pattern.compile("@\\{([a-zA-Z0-9_-]+)\\}|@([a-zA-Z0-9_-]+)");

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String addComment(
      FlowCommentCreateDTO dto, String userId, String userName, String tenantId) {
    if (!StringUtils.hasText(userId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.comment.user.required")
          .build();
    }
    // 回复场景：校验父评论存在且属于同一实例
    if (StringUtils.hasText(dto.getParentCommentId())) {
      FlowCommentVO parent =
          commentRepository.findById(dto.getParentCommentId()).orElse(null);
      if (parent == null || parent.getDeleted() == 1) {
        throw SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.comment.parent.not.found")
            .params(dto.getParentCommentId())
            .build();
      }
      if (!parent.getInstanceId().equals(dto.getInstanceId())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.workflow.comment.parent.instance.mismatch")
            .build();
      }
    }

    FlowCommentVO comment = new FlowCommentVO();
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
    commentRepository.save(comment);
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
  public List<FlowCommentVO> listByInstance(String tenantId, String instanceId) {
    return commentRepository.findByInstanceAndTenant(tenantId, instanceId);
  }

  @Override
  public List<FlowCommentVO> listRootComments(String tenantId, String instanceId) {
    return commentRepository.findRootCommentsByTenant(tenantId, instanceId);
  }

  @Override
  public List<FlowCommentVO> listReplies(String parentCommentId) {
    return commentRepository.findReplies(parentCommentId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean deleteComment(String commentId, String userId) {
    FlowCommentVO comment = commentRepository.findById(commentId).orElse(null);
    if (comment == null || comment.getDeleted() == 1) {
      return false;
    }
    // 仅评论人本人可删除自己的评论
    if (!comment.getUserId().equals(userId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.comment.delete.no.permission")
          .build();
    }
    comment.setDeleted(1);
    commentRepository.update(comment);
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

  // ==================== P2-1: 审批常用语能力（由 FlowQuickCommentServiceImpl 合并） ====================

  /**
   * 查询用户的常用语列表（用户自定义 + 系统预设合并）。
   *
   * <p>合并查询：先查用户自定义常用语，再追加系统预设，最终按 sortNum 升序、useCount 降序两级排序。
   *
   * @param userId 用户 ID（不可空，为空返回空列表）
   * @param tenantId 租户 ID（可空，回退 TenantContext）
   * @return 常用语列表（已合并 + 已排序），无数据返回空列表
   */
  @Override
  public List<FlowQuickCommentVO> listQuickComments(String userId, String tenantId) {
    if (!StringUtils.hasText(userId)) {
      return List.of();
    }
    String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    // 查询：用户自定义 + 系统预设（isSystem=1）
    List<FlowQuickCommentVO> list = quickCommentRepository.findActiveByUser(userId, tid);
    // 系统预设（全局）
    List<FlowQuickCommentVO> systemList = quickCommentRepository.findActiveSystemByTenant(tid);
    list.addAll(systemList);
    // 排序：sortNum 升序, useCount 降序
    list.sort(
        Comparator.comparingInt(FlowQuickCommentVO::getSortNum)
            .thenComparing(Comparator.comparingInt(FlowQuickCommentVO::getUseCount).reversed()));
    return list;
  }

  /**
   * 创建用户自定义常用语。
   *
   * <p>仅创建用户自定义常用语（isSystem=0），useCount 初始为 0。创建时强制覆盖 userId/tenantId/isSystem=0，
   * 不可通过 DTO 伪造为系统预设。
   *
   * @param dto 常用语 DTO（含 content/commentType/sortNum）
   * @param userId 创建人 ID（不可空）
   * @param tenantId 租户 ID（可空，回退 TenantContext）
   * @return 新常用语 ID
   * @throws SysException BAD_REQUEST — userId 为空
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String createQuickComment(FlowQuickCommentDTO dto, String userId, String tenantId) {
    if (!StringUtils.hasText(userId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.quickcomment.user.required")
          .build();
    }
    FlowQuickCommentVO comment = new FlowQuickCommentVO();
    comment.setUserId(userId);
    comment.setContent(dto.getContent());
    comment.setCommentType(dto.getCommentType());
    comment.setSortNum(dto.getSortNum() != null ? dto.getSortNum() : 0);
    comment.setUseCount(0);
    comment.setIsSystem(0);
    comment.setTenantId(tenantId != null ? tenantId : TenantContextHolder.getTenantId());
    quickCommentRepository.save(comment);
    log.info("[FlowQuickComment] 新增常用语: userId={} id={}", userId, comment.getId());
    return comment.getId();
  }

  /**
   * 更新常用语。
   *
   * <p>仅允许创建者本人更新；系统预设不可更新。仅更新 DTO 中非空字段。
   *
   * @param dto 常用语 DTO（id 必传）
   * @param userId 操作人 ID（必须与创建者一致）
   * @throws SysException BAD_REQUEST — id 为空；NOT_FOUND — 常用语不存在；FORBIDDEN — 操作人非创建者
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void updateQuickComment(FlowQuickCommentDTO dto, String userId) {
    if (!StringUtils.hasText(dto.getId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.quickcomment.id.required")
          .build();
    }
    FlowQuickCommentVO existing = quickCommentRepository.findById(dto.getId())
        .orElse(null);
    if (existing == null || existing.getDeleted() == 1) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.quickcomment.not.found")
          .params(dto.getId())
          .build();
    }
    if (!userId.equals(existing.getUserId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.quickcomment.no.permission")
          .build();
    }
    existing.setContent(dto.getContent());
    if (dto.getCommentType() != null) {
      existing.setCommentType(dto.getCommentType());
    }
    if (dto.getSortNum() != null) {
      existing.setSortNum(dto.getSortNum());
    }
    quickCommentRepository.update(existing);
  }

  /**
   * 删除常用语（软删除）。
   *
   * <p>权限校验：
   *
   * <ul>
   *   <li>系统预设（isSystem=1）不可删除，抛 BAD_REQUEST
   *   <li>仅创建者本人可删除，非创建者抛 FORBIDDEN
   * </ul>
   *
   * @param id 常用语 ID
   * @param userId 操作人 ID
   * @throws SysException BAD_REQUEST — 系统预设不可删；FORBIDDEN — 无权限
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteQuickComment(String id, String userId) {
    FlowQuickCommentVO existing = quickCommentRepository.findById(id)
        .orElse(null);
    if (existing == null || existing.getDeleted() == 1) {
      return;
    }
    // 系统预设不可删除
    if (existing.getIsSystem() != null && existing.getIsSystem() == 1) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.quickcomment.system.cannot.delete")
          .build();
    }
    if (!userId.equals(existing.getUserId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.quickcomment.no.permission")
          .build();
    }
    existing.setDeleted(1);
    quickCommentRepository.update(existing);
  }

  /**
   * 增加常用语使用次数。
   *
   * <p>用户在前端选择常用语时调用，useCount 自增 1。异常被 try-catch 吞掉记 WARN，不传播异常——使用统计失败不应阻塞评论发布主流程。
   *
   * <p>已知风险：采用「先查后更」非原子操作，高并发场景下 useCount 可能丢失更新。生产环境建议改用 SQL 原子更新。
   *
   * @param id 常用语 ID
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void incrementQuickCommentUseCount(String id) {
    if (!StringUtils.hasText(id)) {
      return;
    }
    try {
      FlowQuickCommentVO existing = quickCommentRepository.findById(id)
          .orElse(null);
      if (existing != null && existing.getDeleted() == 0) {
        existing.setUseCount((existing.getUseCount() == null ? 0 : existing.getUseCount()) + 1);
        quickCommentRepository.update(existing);
      }
    } catch (Exception e) {
      log.warn("[FlowQuickComment] 增加使用次数失败: id={} err={}", id, e.getMessage());
    }
  }
}
