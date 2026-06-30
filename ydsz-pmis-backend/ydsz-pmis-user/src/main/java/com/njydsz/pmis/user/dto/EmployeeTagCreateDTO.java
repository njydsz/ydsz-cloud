package com.njydsz.pmis.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人员标签 DTO
 */
@Data
public class EmployeeTagCreateDTO {

    @NotNull(message = "员工 ID 不能为空")
    private Long employeeId;

    @NotBlank(message = "标签类型不能为空")
    private String tagType;         // SKILL/INDUSTRY/DOMAIN/CERT

    @NotBlank(message = "标签编码不能为空")
    private String tagCode;

    @NotBlank(message = "标签名称不能为空")
    private String tagName;

    private Integer proficiency;    // 1-5
    private Integer yearsExp;
    private String remark;
}
