package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 满意度评价 DTO
 */
@Data
public class SatisfactionCreateDTO {
    private String surveyCode;
    private Long initiationId;
    private Long ticketId;
    private Long warrantyId;
    /** 总体评分 1-5 */
    private Integer score;
    private Integer professionalism;
    private Integer timeliness;
    private Integer quality;
    private Integer attitude;
    private String comments;
    private String suggest;
    private Boolean anonymous;
    private Long evaluatorId;
    private String evaluatorName;
}
