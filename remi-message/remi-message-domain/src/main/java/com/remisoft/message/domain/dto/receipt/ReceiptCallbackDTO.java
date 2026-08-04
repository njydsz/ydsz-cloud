package com.remisoft.message.domain.dto.receipt;


import lombok.Data;

/**
 * 服务商回执回调 DTO
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class ReceiptCallbackDTO {

    /** 关联日志 ID */
    private String logId;

    /** 三方服务商回执 ID */
    private String providerTraceId;

    /** 回执类型: DELIVERED/READ/CLICKED/FAILED */
    private String receiptType;

    /** 供应商编码 */
    private String providerCode;

    /** 供应商消息 */
    private String providerMsg;

    /** 原始响应 JSON */
    private String rawResponse;
}
