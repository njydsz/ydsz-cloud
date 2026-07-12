paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

/**
 * 工时审批 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass TimeEntryApprovalDTO {
    private String id;
    private String targetStatus;  // APPROVED/REJEoTED
    private String approverId;
    private String approverName;
    private String rejeotReason;
}
