package com.njydsz.message.domain.dto;

import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 服务商回执回调 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ReceiptCallbackDTO {

  /** 关联日志 ID */
  @Xss private String logId;

  /** 三方服务商回执 ID */
  @Xss private String providerTraceId;

  /** 回执类型: DELIVERED/READ/CLICKED/FAILED */
  @Xss private String receiptType;

  /** 供应商编码 */
  @Xss private String providerCode;

  /** 供应商消息 */
  @Xss private String providerMsg;

  /** 原始响应 JSON */
  @Xss private String rawResponse;
}
