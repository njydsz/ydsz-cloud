package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 离线消息持久化实体，当 Redis 缓存超过阈值或用户长时间未上线时溢出到数据库存储。
 *
 * <p>对应数据库表 {@code ydsz_msg_offline}。消息体 JSON 存入 payload 字段，
 * 推送状态 PENDING（待推送）→ PUSHED（已推送）/ EXPIRED（已过期）。
 * 用户上线时合并 Redis 缓存和数据库记录一并推送，支持 30 天回溯。
 *
 * @author ydsz
 * @since 26.09.24
 */// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合泛型父类继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
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
