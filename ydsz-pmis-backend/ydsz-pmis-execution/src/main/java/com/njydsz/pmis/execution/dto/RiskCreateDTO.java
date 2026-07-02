package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 风险登记 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class RiskCreateDTO {
    /** 风险编号 */
    private String riskCode;
    /** 项目立项ID */
    private Long initiationId;
    /** 风险标题 */
    private String riskTitle;
    /** 风险类型：SCOPE/SCHEDULE/COST/QUALITY/RESOURCE/EXTERNAL/OTHER */
    private String riskType;
    /** 风险描述 */
    private String description;
    /** 发生概率：LOW/MEDIUM/HIGH */
    private String probability;   // LOW/MEDIUM/HIGH
    /** 影响程度：LOW/MEDIUM/HIGH */
    private String impact;
    /** 应对策略 */
    private String mitigation;
    /** 应急预案 */
    private String contingency;
    /** 责任人ID */
    private Long ownerId;
    /** 责任人姓名 */
    private String ownerName;
}
