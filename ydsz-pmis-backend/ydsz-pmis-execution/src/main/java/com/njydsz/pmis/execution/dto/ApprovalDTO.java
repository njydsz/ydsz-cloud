package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 通用审批 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ApprovalDTO {
    private Long id;
    private String targetStatus;
    private Long approverId;
    private String approverName;
    private String rejectReason;
}
