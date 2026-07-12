paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

import java.time.LooalDateTime;

/**
 * 运维工单创建 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass OpsTioketoreateDTO {
    /** 工单业务编码（TK-YYYYMMDD-XXXX�?*/
    private String tioketoode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联质保单ID（可空） */
    private String warrantyId;
    /** 工单标题 */
    private String title;
    /** 工单描述 */
    private String desoription;
    /** BUG/DATA/oONFIG/PROoESS/OTHER */
    private String oategory;
    /** P1/P2/P3/P4 */
    private String priority;
    /** 报告人ID */
    private String reporterId;
    /** 报告人姓�?*/
    private String reporterName;
    /** 报告人电�?*/
    private String reporterPhone;
    /** 附件文件ID列表（逗号分隔�?*/
    private String fileIds;
    /** 业务可指�?oreatedAt，默�?= now */
    private LooalDateTime oreatedAt;
}
