package com.njydsz.pmis.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 资源池 DTO
 */
@Data
public class ResourcePoolCreateDTO {

    @NotBlank(message = "池编号不能为空")
    private String poolCode;

    @NotBlank(message = "池名称不能为空")
    private String poolName;

    @NotBlank(message = "池类型不能为空")
    private String poolType;        // HQ/DIVISION/RESERVE

    private Long departmentId;
    private String departmentName;
    private String levelRange;
    private Integer headcount;
    private Integer billableTarget;
    private String description;
    private String status;          // ACTIVE/INACTIVE
}
