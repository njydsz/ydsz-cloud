package com.njydsz.pmis.project.dto.aftersales;

import lombok.Data;

/**
 * 满意度评价 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class SatisfactionCreateDTO {
    /** 评价业务编码（SV-YYYYMMDD-XXXX） */
    private String surveyCode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联工单ID（可空） */
    private String ticketId;
    /** 关联质保单ID（可空） */
    private String warrantyId;
    /** 总体评分 1-5 */
    private Integer score;
    /** 专业度评分 1-5 */
    private Integer professionalism;
    /** 及时性评分 1-5 */
    private Integer timeliness;
    /** 质量评分 1-5 */
    private Integer quality;
    /** 服务态度评分 1-5 */
    private Integer attitude;
    /** 评价意见 */
    private String comments;
    /** 改进建议 */
    private String suggest;
    /** 是否匿名评价 */
    private Boolean anonymous;
    /** 评价人ID */
    private String evaluatorId;
    /** 评价人姓名 */
    private String evaluatorName;
}
