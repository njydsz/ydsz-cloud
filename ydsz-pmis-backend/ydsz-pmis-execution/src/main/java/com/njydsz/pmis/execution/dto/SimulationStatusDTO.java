package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 利润测算状态迁移 DTO
 */
@Data
public class SimulationStatusDTO {

    @NotNull(message = "测算 ID 不能为空")
    private Long id;

    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    private String approverName;
    private String approvalComment;
}
