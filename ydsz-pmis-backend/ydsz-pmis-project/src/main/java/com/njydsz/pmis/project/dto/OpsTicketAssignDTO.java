package com.njydsz.pmis.project.dto;

import lombok.Data;

/**
 * 运维工单派单 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class OpsTicketAssignDTO {
    /** 工单ID */
    private Long id;
    /** 处理人ID */
    private Long assigneeId;
    /** 处理人姓名 */
    private String assigneeName;
}
