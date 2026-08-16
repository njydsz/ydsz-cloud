package com.njydsz.message.domain.entity.config;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * P0-3: 离线消息持久化表。
 *
 * <p>当 Redis 离线消息缓存超过阈值或用户长时间未上线时， 将消息从 Redis 溢出到数据库持久化存储，支持 30 天回溯。 用户上线时合并 Redis 缓存和数据库记录一并推送。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_offline")
public class MsgOffline extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 接收人用户 ID */
  private String userId;

  /** 消息类型标签（如 NOTIFICATION / ALERT） */
  private String msgType;

  /** 消息内容 JSON */
  private String payload;

  /** 消息时间戳（毫秒） */
  private Long msgTimestamp;

  /** 推送状态: PENDING 待推送 / PUSHED 已推送 / EXPIRED 已过期 */
  private String status;

  /** 推送时间 */
  private LocalDateTime pushedAt;

  /** 过期时间（默认 createdAt + 30 天） */
  private LocalDateTime expiredAt;
}
