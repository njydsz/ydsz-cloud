package com.njydsz.pmis.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 合同签订事件（Sales → Finance + Project）
 *
 * <p>当销售服务中合同状态变为 SIGNED 时发布此事件：
 * <ul>
 *   <li>Finance 消费：创建回款计划、初始化收入确认</li>
 *   <li>Project 消费：自动创建立项</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContractSignedEvent extends CrossDomainEvent {

    private static final long serialVersionUID = 1L;

    /** 合同 ID */
    private String contractId;
    /** 合同编码 */
    private String contractCode;
    /** 立项 ID（关联合同的立项） */
    private String initiationId;
    /** 客户 ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 合同金额 */
    private BigDecimal contractAmount;
    /** 合同签订日期 */
    private String signedDate;
    /** 项目类型 */
    private String projectType;
}
