package com.njydsz.pmis.execution.dto;

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
    @NotNull(message = "测算 ID 不能为空")
    private Long id;

    /** 目标状态：SimulationStatus.code */
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    /** 审批人姓名 */
    private String approverName;
    /** 审批意见 */
    private String approvalComment;
}
