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
 * 消息回执实体，记录服务商送达/已读/点击/失败的回调数据。
 *
 * <p>对应数据库表 {@code ydsz_msg_receipt}。关联日志表的 logId 字段回查原始发送记录，
 * providerTraceId 为服务商侧回执唯一标识。receiptType 区分事件类型：
 * DELIVERED（送达）、READ（已读）、CLICKED（点击）、FAILED（失败）。
 *
 * @author ydsz
 * @since 26.09.24
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_receipt")
public class MsgReceipt extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 关联 ydsz_msg_log.id */
  private String logId;

  /** 三方服务商回执 ID */
  private String providerTraceId;

  /** 回执类型: DELIVERED 送达 / READ 已读 / CLICKED 点击 / FAILED 失败 */
  private String receiptType;

  /** 回执时间 */
  private LocalDateTime receiptTime;

  /** 供应商编码 */
  private String providerCode;

  /** 供应商消息 */
  private String providerMsg;

  /** 原始响应 JSON */
  private String rawResponse;
}
