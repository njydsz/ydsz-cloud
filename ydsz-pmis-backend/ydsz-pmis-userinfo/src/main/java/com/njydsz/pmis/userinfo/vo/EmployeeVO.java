package com.njydsz.pmis.userinfo.vo;

import com.njydsz.pmis.userinfo.entity.user.EmployeeDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 员工视图对象（含部门 / 岗位 / 职级名称装配）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "员工视图")
public class EmployeeVO extends EmployeeDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门名称 */
    private String departmentName;

    /** 岗位名称 */
    private String positionName;

    /** 职级名称 */
    private String levelName;

    /** 兼职费率名称（仅 PART_TIME 类型） */
    private String partTimeRateName;

    /** 外包费率名称（仅 OUTSOURCE 类型） */
    private String outsourceRateName;
}
