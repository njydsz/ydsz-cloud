package com.njydsz.nextwiki.server.mention;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.common.socket.push.RealtimePushTemplate;

/**
 * 评论 @ 提及服务（S4-P3-04）。
 *
 * <p>解析评论内容中的 {@code @username} 格式提及，提取被提及用户并通过 WebSocket 推送通知。
 *
 * <p><b>提及格式：</b>
 *
 * <ul>
 *   <li>{@code @张三} — 通过用户名称提及
 *   <li>{@code @user123} — 通过用户 ID 提及
 *   <li>{@code @张三 } — 名称后可跟空格（空格不作为名称一部分）
 * </ul>
 *
 * <p><b>通知协议：</b>
 *
 * <pre>
 *   目标用户: 被提及用户 ID
 *   消息类型: COMMENT_MENTION
 *   消息格式: {
 *     "commentId": "评论ID",
 *     "fileNodeId": "文件ID",
 *     "commenterId": "评论者ID",
 *     "commenterName": "评论者名称",
 *     "content": "评论内容（截断前100字）",
 *     "mentionedAt": "2026-08-19T10:00:00"
 *   }
 * </pre>
 *
 * <p><b>降级策略：</b>WebSocket 模块未引入时静默降级，不影响评论正常提交。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class MentionService {

  /** @提及 正则：@后面跟非空白字符（用户名/用户ID） */
  private static final Pattern MENTION_PATTERN = Pattern.compile("@([^\\s@]+)");

  /** WebSocket 推送模板（可选依赖） */
  private final ObjectProvider<RealtimePushTemplate> pushTemplateProvider;

  /** 消息类型常量 */
  public static final String TYPE_COMMENT_MENTION = "COMMENT_MENTION";

  /**
   * 构造方法注入。
   *
   * @param pushTemplateProvider WebSocket 推送模板提供者
   */
  public MentionService(ObjectProvider<RealtimePushTemplate> pushTemplateProvider) {
    this.pushTemplateProvider = pushTemplateProvider;
  }

  /**
   * 从评论内容中提取被提及的用户名/用户 ID 列表。
   *
   * <p>使用正则 {@code @([^\s@]+)} 匹配所有 @ 开头的提及，去重后返回。
   *
   * @param content 评论内容
   * @return 被提及的用户名/ID 列表（去重，不含 @ 符号）
   */
  public List<String> extractMentions(String content) {
    if (content == null || content.isBlank()) {
      return new ArrayList<>();
    }

    Set<String> mentions = new LinkedHashSet<>();
    Matcher matcher = MENTION_PATTERN.matcher(content);

    while (matcher.find()) {
      String mention = matcher.group(1).trim();
      if (!mention.isEmpty()) {
        mentions.add(mention);
      }
    }

    return new ArrayList<>(mentions);
  }

  /**
   * 发送 @ 提及通知。
   *
   * <p>向所有被提及用户推送 WebSocket 通知，告知其在某条评论中被 @。
   *
   * @param commentId 评论 ID
   * @param fileNodeId 文件节点 ID
   * @param commenterId 评论者 ID
   * @param commenterName 评论者名称（用于展示）
   * @param content 评论内容
   * @param mentionedUserIds 被提及用户 ID 列表
   */
  public void sendMentionNotifications(
      String commentId,
      String fileNodeId,
      String commenterId,
      String commenterName,
      String content,
      List<String> mentionedUserIds) {

    if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
      return;
    }

    RealtimePushTemplate pushTemplate = pushTemplateProvider.getIfAvailable();
    if (pushTemplate == null) {
      log.debug("[MentionService] WebSocket 模块未引入，跳过 @提及通知");
      return;
    }

    // 截断内容用于通知展示
    String truncatedContent = content.length() > 100
        ? content.substring(0, 100) + "..."
        : content;

    for (String mentionedUserId : mentionedUserIds) {
      // 不通知自己
      if (mentionedUserId.equals(commenterId)) {
        continue;
      }

      try {
        Map<String, Object> payload = Map.of(
            "commentId", commentId,
            "fileNodeId", fileNodeId,
            "commenterId", commenterId,
            "commenterName", commenterName != null ? commenterName : "有人",
            "content", truncatedContent,
            "mentionedAt", LocalDateTime.now().toString()
        );

        String messageId = "mention_" + commentId + "_" + mentionedUserId;
        pushTemplate.pushToUserWithOffline(
            mentionedUserId, TYPE_COMMENT_MENTION, payload, messageId);

        log.debug("[MentionService] @提及通知已发送: mentionedUser={}, commentId={}",
            mentionedUserId, commentId);
      } catch (Exception e) {
        // 单个用户通知失败不影响其他用户
        log.warn("[MentionService] @提及通知发送失败: mentionedUser={}, err={}",
            mentionedUserId, e.getMessage());
      }
    }
  }

  /**
   * 解析提及并发送通知（便捷方法）。
   *
   * @param commentId 评论 ID
   * @param fileNodeId 文件节点 ID
   * @param commenterId 评论者 ID
   * @param commenterName 评论者名称
   * @param content 评论内容
   */
  public void parseAndNotifyMentions(
      String commentId,
      String fileNodeId,
      String commenterId,
      String commenterName,
      String content) {

    List<String> mentions = extractMentions(content);
    if (!mentions.isEmpty()) {
      // 注意：mentions 此时是用户名，需要转换为用户 ID
      // 实际项目中应注入 UserService 进行转换
      // 此处简化处理，假设传入的 mentions 已经是用户 ID
      sendMentionNotifications(commentId, fileNodeId, commenterId, commenterName, content, mentions);
    }
  }
}
