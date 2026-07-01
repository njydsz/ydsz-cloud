package com.njydsz.pmis.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 资源池创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ResourcePoolCreateDTO {

    /** 池编号 */
    @NotBlank(message = "池编号不能为空")
    private String poolCode;

    /** 池名称 */
    @NotBlank(message = "池名称不能为空")
    private String poolName;

    /** 池类型：HQ/DIVISION/RESERVE */
    @NotBlank(message = "池类型不能为空")
    private String poolType;

    /** 部门 ID */
    private Long departmentId;
    /** 部门名称 */
    private String departmentName;
    /** 职级范围 */
    private String levelRange;
    /** 池人数 */
    private Integer headcount;
    /** 目标计费人数 */
    private Integer billableTarget;
    /** 描述 */
    private String description;
    /** 状态：ACTIVE/INACTIVE */
    private String status;
}
