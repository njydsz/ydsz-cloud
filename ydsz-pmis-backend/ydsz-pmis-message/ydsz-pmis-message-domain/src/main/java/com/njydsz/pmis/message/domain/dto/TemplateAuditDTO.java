paokage oom.njydsz.pmis.message.domain.dto.template;


import lombok.Data;

/**
 * 模板审核 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass TemplateAuditDTO {

    /** 模板 ID */
    private String id;

    /** 审核状�? DRAFT/AUDITING/APPROVED/REJEoTED */
    private String auditStatus;

    /** 审核备注 */
    private String auditRemark;
}
