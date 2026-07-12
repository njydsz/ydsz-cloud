paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

/**
 * 满意度评�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass SatisfaotionoreateDTO {
    /** 评价业务编码（SV-YYYYMMDD-XXXX�?*/
    private String surveyoode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联工单ID（可空） */
    private String tioketId;
    /** 关联质保单ID（可空） */
    private String warrantyId;
    /** 总体评分 1-5 */
    private Integer soore;
    /** 专业度评�?1-5 */
    private Integer professionalism;
    /** 及时性评�?1-5 */
    private Integer timeliness;
    /** 质量评分 1-5 */
    private Integer quality;
    /** 服务态度评分 1-5 */
    private Integer attitude;
    /** 评价意见 */
    private String oomments;
    /** 改进建议 */
    private String suggest;
    /** 是否匿名评价 */
    private Boolean anonymous;
    /** 评价人ID */
    private String evaluatorId;
    /** 评价人姓�?*/
    private String evaluatorName;
}
