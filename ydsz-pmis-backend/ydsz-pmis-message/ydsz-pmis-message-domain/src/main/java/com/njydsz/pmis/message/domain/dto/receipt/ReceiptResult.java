package com.njydsz.pmis.message.domain.dto.receipt;


import com.njydsz.pmis.message.domain.enums.receipt.ReceiptStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主动拉取的回执结果（P2-9）。
 *
 * <p>由 {@link com.njydsz.pmis.message.server.channel.MessageChannel#queryReceipt} 返回，
 * 描述从服务商侧查询到的最新回执状态。{@code ReceiptPuller} 拿到此结果后会联动更新
 * {@code MsgLogDO.receiptStatus} 与 {@code MsgLogDO.receiptAt}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResult {

    /** 回执状态（DELIVERED/READ/CLICKED/FAILED） */
    private ReceiptStatusEnum status;

    /** 服务商侧消息（如"DELIVERED"、"REJECTED"等描述） */
    private String providerMsg;

    /** 原始响应 JSON（用于排查） */
    private String rawResponse;

    /**
     * 构造指定状态的回执结果。
     *
     * @param status 回执状态
     * @return 回执结果实例
     */
    public static ReceiptResult of(ReceiptStatusEnum status) {
        return new ReceiptResult(status, null, null);
    }

    /**
     * 构造指定状态与描述的回执结果。
     *
     * @param status      回执状态
     * @param providerMsg 服务商消息
     * @return 回执结果实例
     */
    public static ReceiptResult of(ReceiptStatusEnum status, String providerMsg) {
        return new ReceiptResult(status, providerMsg, null);
    }
}
