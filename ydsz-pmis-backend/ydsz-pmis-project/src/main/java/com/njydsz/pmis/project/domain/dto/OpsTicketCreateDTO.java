package com.njydsz.pmis.project.domain.dto;

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
    /** 工单业务编码（TK-YYYYMMDD-XXXX） */
    private String ticketCode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联质保单ID（可空） */
    private String warrantyId;
    /** 工单标题 */
    private String title;
    /** 工单描述 */
    private String description;
    /** BUG/DATA/CONFIG/PROCESS/OTHER */
    private String category;
    /** P1/P2/P3/P4 */
    private String priority;
    /** 报告人ID */
    private String reporterId;
    /** 报告人姓名 */
    private String reporterName;
    /** 报告人电话 */
    private String reporterPhone;
    /** 附件文件ID列表（逗号分隔） */
    private String fileIds;
    /** 业务可指定 createdAt，默认 = now */
    private LocalDateTime createdAt;
}
