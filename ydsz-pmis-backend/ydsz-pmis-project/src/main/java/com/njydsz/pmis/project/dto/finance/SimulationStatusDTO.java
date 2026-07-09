package com.njydsz.pmis.project.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 利润测算状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class SimulationStatusDTO {

    /** 测算版本ID */
    @NotNull(message = "{validation.execution.msg_06417837}")
    private String id;

    /** 目标状态：SimulationStatus.code */
    @NotBlank(message = "{validation.execution.msg_8304cf7d}")
    private String targetStatus;

    /** 审批人姓名 */
    private String approverName;
    /** 审批意见 */
    private String approvalComment;
}
