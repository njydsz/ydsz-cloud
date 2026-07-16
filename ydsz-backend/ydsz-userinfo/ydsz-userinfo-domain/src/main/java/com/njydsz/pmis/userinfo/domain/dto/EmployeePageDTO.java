package com.njydsz.userinfo.domain.dto.user;

import java.io.Serial;

import com.njydsz.common.domain.query.PageQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 员工分页查询 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "员工分页查询")
public class EmployeePageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门 ID */
    @Schema(description = "部门 ID")
    private String departmentId;

    /** 雇佣类型：FULL_TIME/PART_TIME/OUTSOURCE */
    @Schema(description = "雇佣类型")
    private String employeeType;

    /** 在职状态 */
    @Schema(description = "在职状态")
    private String workStatus;
}
