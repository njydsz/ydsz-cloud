package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 运维工单状态变更 DTO
 */
@Data
public class OpsTicketStatusDTO {
    private Long id;
    /** OpsTicketStatus.code */
    private String targetStatus;
    private String resolutionNote;
    private Integer customerScore;
    private String customerComment;
}
