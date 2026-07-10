package com.njydsz.pmis.userinfo.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人员标签创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class EmployeeTagCreateDTO {

    /** 员工 ID */
    @NotNull(message = "{validation.user.msg_03f5ae35}")
    private String employeeId;

    /** 标签类型：SKILL/INDUSTRY/DOMAIN/CERT */
    @NotBlank(message = "{validation.user.msg_969983ae}")
    private String tagType;

    /** 标签编码 */
    @NotBlank(message = "{validation.user.msg_8faabfac}")
    private String tagCode;

    /** 标签名称 */
    @NotBlank(message = "{validation.user.msg_16eb3ef6}")
    private String tagName;

    /** 熟练度 1-5 */
    private Integer proficiency;
    /** 经验年限 */
    private Integer yearsExp;
    /** 备注 */
    private String remark;
}
