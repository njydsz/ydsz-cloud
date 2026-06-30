package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 运维工单派单 DTO
 */
@Data
public class OpsTicketAssignDTO {
    private Long id;
    private Long assigneeId;
    private String assigneeName;
}
