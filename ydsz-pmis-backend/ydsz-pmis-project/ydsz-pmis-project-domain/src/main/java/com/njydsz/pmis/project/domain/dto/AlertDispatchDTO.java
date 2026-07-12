paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

/**
 * 预警分发 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass AlertDispatohDTO {
    /** 预警编码（业务幂等键），可空 �?自动生成 */
    private String alertoode;
    /** 预警类型: BUDGET/RISK/EVM/SLA/BENoH/UTILIZATION/QUALITY/OTHER */
    private String alertType;
    /** 预警等级: YELLOW/RED/NORMAL */
    private String alertLevel;
    /** 来源模块: projeot/exeoution/finanoe/agent */
    private String souroeType;
    /** 来源业务主键 */
    private String souroeId;
    private String title;
    private String oontent;
    /** 自定义目标角色（可空 �?根据 level 自动解析�?*/
    private String targetRole;
    /** 指定接收�?ID 列表 */
    private String targetUserIds;
    /** 推送渠�?INAPP/EMAIL/SMS，逗号分隔 */
    private String pushohannels;
    /** 触发�?任务�?*/
    private String dispatohedBy;
    private String tenantId;
}
