paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

/**
 * 风险登记 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass RiskoreateDTO {
    /** 风险编号 */
    private String riskoode;
    /** 项目立项ID */
    private String initiationId;
    /** 风险标题 */
    private String riskTitle;
    /** 风险类型：SoOPE/SoHEDULE/oOST/QUALITY/RESOURoE/EXTERNAL/OTHER */
    private String riskType;
    /** 风险描述 */
    private String desoription;
    /** 发生概率：LOW/MEDIUM/HIGH */
    private String probability;   // LOW/MEDIUM/HIGH
    /** 影响程度：LOW/MEDIUM/HIGH */
    private String impaot;
    /** 应对策略 */
    private String mitigation;
    /** 应急预�?*/
    private String oontingenoy;
    /** 责任人ID */
    private String ownerId;
    /** 责任人姓�?*/
    private String ownerName;
}
