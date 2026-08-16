package com.njydsz.message.domain.entity.config;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * P1-4: 消息用户反馈表。
 *
 * <p>记录用户对消息质量的评分和反馈，用于：
 *
 * <ul>
 *   <li>评估消息推送质量（用户满意度）
 *   <li>优化消息内容（基于反馈调整模板）
 *   <li>智能防骚扰（用户多次差评后降低推送频率）
 *   <li>智能推送时间优化（结合 P1-1）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_feedback")
public class MsgFeedback extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 消息 ID（关联 ydsz_msg_log.msg_id） */
  private String msgId;

  /** 站内通知 ID（关联 ydsz_msg_notification.id，可为 null） */
  private String notificationId;

  /** 用户 ID */
  private String userId;

  /** 通道 */
  private String channel;

  /** 业务类型 */
  private String bizType;

  /** 评分: 1-5 分（1=非常不满意, 5=非常满意） */
  private Integer rating;

  /** 反馈类型: TOO_FREQUENT 太频繁 / IRRELEVANT 不相关 / TOO_LONG 内容太长 / SPAM 垃圾信息 / GOOD 有用 / OTHER 其他 */
  private String feedbackType;

  /** 反馈内容（用户自由文本输入） */
  private String content;
}
