package com.njydsz.pmis.project.domain.dto;

import lombok.Data;

/**
 * 通用审批 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ApprovalDTO {
    private String id;
    private String targetStatus;
    private String approverId;
    private String approverName;
    private String rejectReason;
}
