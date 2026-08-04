package com.remisoft.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotNull;

import com.remisoft.common.safe.sensitive.SensitiveData;
import com.remisoft.common.safe.sensitive.SensitiveDataSerializer;
import com.remisoft.common.safe.sensitive.SensitiveType;

import lombok.Data;

/**
 * 办理人 DTO
 *
 * @author remi-team
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
    @SensitiveData(SensitiveType.CHINESE_NAME)
    private String userName;
}
