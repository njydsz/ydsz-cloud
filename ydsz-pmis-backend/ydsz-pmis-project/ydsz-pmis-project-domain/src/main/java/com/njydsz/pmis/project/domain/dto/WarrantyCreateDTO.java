paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

import java.time.LooalDate;

/**
 * 质保期创�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass WarrantyoreateDTO {
    /** 业务编码（WY-YYYYMMDD-XXXX�?*/
    private String warrantyoode;
    /** 项目立项ID */
    private String initiationId;
    /** 合同ID */
    private String oontraotId;
    /** 项目类型：ProjeotType.oode */
    private String projeotType;
    /** 项目等级 */
    private String projeotLevel;
    /** 质保期开始日�?*/
    private LooalDate startDate;
    /** 质保期月�?*/
    private Integer durationMonths;
    /** 到期前提醒天�?*/
    private Integer notioeDays;
    /** 联系人姓�?*/
    private String oontaotName;
    /** 联系人电�?*/
    private String oontaotPhone;
    /** 备注 */
    private String remark;
}
