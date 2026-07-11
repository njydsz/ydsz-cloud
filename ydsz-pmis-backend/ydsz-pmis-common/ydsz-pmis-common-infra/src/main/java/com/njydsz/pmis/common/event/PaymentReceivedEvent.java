package com.njydsz.pmis.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 回款到账事件（Finance → Project + Sales）
 *
 * <p>当财务服务确认回款到账时发布此事件：
 * <ul>
 *   <li>Project 消费：更新项目预算可用额</li>
 *   <li>Sales 消费：更新合同回款进度</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentReceivedEvent extends CrossDomainEvent {

    private static final long serialVersionUID = 1L;

    /** 回款 ID */
    private String paymentId;
    /** 回款编码 */
    private String paymentCode;
    /** 合同 ID */
    private String contractId;
    /** 立项 ID */
    private String initiationId;
    /** 回款金额 */
    private BigDecimal paymentAmount;
    /** 回款日期 */
    private String paymentDate;
    /** 回款方式 */
    private String paymentMethod;
}
