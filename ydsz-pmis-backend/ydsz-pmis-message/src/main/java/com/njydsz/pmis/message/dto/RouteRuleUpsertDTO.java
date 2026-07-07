package com.njydsz.pmis.message.dto;

import lombok.Data;

/**
 * 路由规则新增/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class RouteRuleUpsertDTO {

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 业务类型 */
    private String bizType;

    /** 通道 */
    private String channel;

    /** 优先级(数值越小越优先) */
    private Integer priority;

    /** 路由条件(SpEL 表达式) */
    private String conditionExpr;

    /** 命中后目标通道 */
    private String targetChannel;

    /** 目标通道发送失败时降级通道 */
    private String fallbackChannel;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 描述说明 */
    private String description;

    /** 排序序号 */
    private Integer sortOrder;
}
