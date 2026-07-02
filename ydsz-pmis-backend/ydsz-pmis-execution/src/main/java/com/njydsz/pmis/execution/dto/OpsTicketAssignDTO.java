package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 运维工单派单 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class OpsTicketAssignDTO {
    private Long id;
    private Long assigneeId;
    private String assigneeName;
}
