package com.njydsz.pmis.project.dto.common;

import lombok.Data;

/**
 * 预警分发 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class AlertDispatchDTO {
    /** 预警编码（业务幂等键），可空 → 自动生成 */
    private String alertCode;
    /** 预警类型: BUDGET/RISK/EVM/SLA/BENCH/UTILIZATION/QUALITY/OTHER */
    private String alertType;
    /** 预警等级: YELLOW/RED/NORMAL */
    private String alertLevel;
    /** 来源模块: project/execution/finance/agent */
    private String sourceType;
    /** 来源业务主键 */
    private String sourceId;
    private String title;
    private String content;
    /** 自定义目标角色（可空 → 根据 level 自动解析） */
    private String targetRole;
    /** 指定接收人 ID 列表 */
    private String targetUserIds;
    /** 推送渠道 INAPP/EMAIL/SMS，逗号分隔 */
    private String pushChannels;
    /** 触发人/任务名 */
    private String dispatchedBy;
    private String tenantId;
}
