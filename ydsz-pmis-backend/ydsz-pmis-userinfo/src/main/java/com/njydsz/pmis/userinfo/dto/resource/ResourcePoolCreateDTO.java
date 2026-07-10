package com.njydsz.pmis.userinfo.dto.resource;

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
    @NotBlank(message = "{validation.user.msg_27b42dc0}")
    private String poolCode;

    /** 池名称 */
    @NotBlank(message = "{validation.user.msg_04617d5a}")
    private String poolName;

    /** 池类型：HQ/DIVISION/RESERVE */
    @NotBlank(message = "{validation.user.msg_92a85357}")
    private String poolType;

    /** 部门 ID */
    private String departmentId;
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
