package com.njydsz.pmis.execution.dto.execution;

import lombok.Data;

/**
 * 工时审批 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class TimeEntryApprovalDTO {
    private String id;
    private String targetStatus;  // APPROVED/REJECTED
    private String approverId;
    private String approverName;
    private String rejectReason;
}
