package com.njydsz.pmis.workflow.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 办理人 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class FlowAssigneeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户类型：USER/ROLE/DEPT */
    @NotNull
    private String userType;

    /** 用户/角色/部门 ID */
    @NotNull
    private String userId;

    /** 姓名 */
    private String userName;
}
