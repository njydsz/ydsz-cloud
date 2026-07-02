package com.njydsz.pmis.execution.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运维工单创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class OpsTicketCreateDTO {
    private String ticketCode;
    private Long initiationId;
    private Long warrantyId;
    private String title;
    private String description;
    /** BUG/DATA/CONFIG/PROCESS/OTHER */
    private String category;
    /** P1/P2/P3/P4 */
    private String priority;
    private Long reporterId;
    private String reporterName;
    private String reporterPhone;
    private String fileIds;
    /** 业务可指定 createdAt，默认 = now */
    private LocalDateTime createdAt;
}
