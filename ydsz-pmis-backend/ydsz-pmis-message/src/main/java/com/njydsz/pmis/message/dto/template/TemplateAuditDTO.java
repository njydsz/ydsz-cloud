package com.njydsz.pmis.message.dto.template;


import lombok.Data;

/**
 * 模板审核 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class TemplateAuditDTO {

    /** 模板 ID */
    private String id;

    /** 审核状态: DRAFT/AUDITING/APPROVED/REJECTED */
    private String auditStatus;

    /** 审核备注 */
    private String auditRemark;
}
