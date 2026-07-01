package com.njydsz.pmis.user.dto;

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
    @NotNull(message = "员工 ID 不能为空")
    private Long employeeId;

    /** 标签类型：SKILL/INDUSTRY/DOMAIN/CERT */
    @NotBlank(message = "标签类型不能为空")
    private String tagType;

    /** 标签编码 */
    @NotBlank(message = "标签编码不能为空")
    private String tagCode;

    /** 标签名称 */
    @NotBlank(message = "标签名称不能为空")
    private String tagName;

    /** 熟练度 1-5 */
    private Integer proficiency;
    /** 经验年限 */
    private Integer yearsExp;
    /** 备注 */
    private String remark;
}
