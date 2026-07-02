package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 工时审批 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class TimeEntryApprovalDTO {
    private Long id;
    private String targetStatus;  // APPROVED/REJECTED
    private Long approverId;
    private String approverName;
    private String rejectReason;
}
