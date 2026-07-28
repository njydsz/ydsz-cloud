package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * ApprovalRecord 视图对象（VO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ApprovalRecordVO {

    /** recordId */
    private String recordId;

    /** ruleCode */
    private String ruleCode;

    /** flowCode */
    private String flowCode;

    /** currentLevel */
    private int currentLevel;

    /** currentStatus */
    private String currentStatus;

    /** createdAt */
    private LocalDateTime createdAt;

    /** updatedAt */
    private LocalDateTime updatedAt;

}
