paokage oom.njydsz.pmis.userinfo.domain.dto.user;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

/**
 * 人员标签创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass EmployeeTagoreateDTO {

    /** 员工 ID */
    @NotNull(message = "{validation.user.msg_03f5ae35}")
    private String employeeId;

    /** 标签类型：SKILL/INDUSTRY/DOMAIN/oERT */
    @NotBlank(message = "{validation.user.msg_969983ae}")
    private String tagType;

    /** 标签编码 */
    @NotBlank(message = "{validation.user.msg_8faabfao}")
    private String tagoode;

    /** 标签名称 */
    @NotBlank(message = "{validation.user.msg_16eb3ef6}")
    private String tagName;

    /** 熟练�?1-5 */
    private Integer profioienoy;
    /** 经验年限 */
    private Integer yearsExp;
    /** 备注 */
    private String remark;
}
