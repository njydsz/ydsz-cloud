package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 工时审批 DTO
 */
@Data
public class TimeEntryApprovalDTO {
    private Long id;
    private String targetStatus;  // APPROVED/REJECTED
    private Long approverId;
    private String approverName;
    private String rejectReason;
}
