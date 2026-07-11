package com.njydsz.pmis.sales.dto.aftersales;

import lombok.Data;

/**
 * 运维工单状态变更 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class OpsTicketStatusDTO {
    /** 工单ID */
    private String id;
    /** OpsTicketStatus.code */
    private String targetStatus;
    /** 解决说明 */
    private String resolutionNote;
    /** 客户评分（1-5） */
    private Integer customerScore;
    /** 客户评价内容 */
    private String customerComment;
}
