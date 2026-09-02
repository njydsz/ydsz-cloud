package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息回执视图对象（VO）。
 *
 * <p>用于返回消息回执的完整信息，包含回执类型、供应商信息及原始响应。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgReceiptVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 回执记录唯一标识（主键） */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** 关联消息日志 ID */
  private String logId;

  /** 三方服务商回执 ID */
  private String providerTraceId;

  /** 回执类型（DELIVERED/READ/CLICKED/FAILED） */
  private String receiptType;

  /** 回执时间 */
  private LocalDateTime receiptTime;

  /** 供应商编码 */
  private String providerCode;

  /** 供应商消息 */
  private String providerMsg;

  /** 原始响应 JSON */
  private String rawResponse;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
