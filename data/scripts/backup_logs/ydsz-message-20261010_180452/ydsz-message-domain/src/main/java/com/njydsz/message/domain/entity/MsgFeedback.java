package com.njydsz.message.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 消息用户反馈实体，记录接收人对推送消息的评分与反馈内容。
 *
 * <p>对应数据库表 {@code ydsz_msg_feedback}。支持评估消息推送满意度、
 * 优化模板内容、智能防骚扰（多次差评降频）及推送时间优化。
 * rating 为 1-5 分评分，feedbackType 标识反馈分类（频繁/不相关/太长/垃圾/有用/其他）。
 *
 * @author ydsz
 * @since 26.09.24
 */// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合泛型父类继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
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
