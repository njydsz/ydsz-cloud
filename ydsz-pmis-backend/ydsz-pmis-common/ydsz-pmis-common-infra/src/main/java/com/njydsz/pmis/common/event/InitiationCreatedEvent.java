package com.njydsz.pmis.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 立项创建事件（Project → Finance + Sales）
 *
 * <p>当项目执行服务创建新立项时发布此事件：
 * <ul>
 *   <li>Finance 消费：初始化项目预算、创建利润快照基线</li>
 *   <li>Sales 消费：关联合同到立项</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InitiationCreatedEvent extends CrossDomainEvent {

    private static final long serialVersionUID = 1L;

    /** 立项 ID */
    private String initiationId;
    /** 立项编码 */
    private String initiationCode;
    /** 项目名称 */
    private String projectName;
    /** 客户 ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 项目类型 */
    private String projectType;
    /** 预算金额 */
    private BigDecimal budgetAmount;
    /** 项目经理 ID */
    private String projectManagerId;
    /** 项目经理姓名 */
    private String projectManagerName;
    /** 计划开始日期 */
    private String plannedStartDate;
    /** 计划结束日期 */
    private String plannedEndDate;
}
