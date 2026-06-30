package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 利润测算 DTO
 */
@Data
public class ProfitSimulationCreateDTO {

    @NotBlank(message = "测算编号不能为空")
    private String simulationCode;

    @NotBlank(message = "测算名称不能为空")
    private String simulationName;

    @NotNull(message = "项目 ID 不能为空")
    private Long initiationId;

    private String scenarioType;      // BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM

    @NotNull(message = "合同金额不能为空")
    private BigDecimal contractAmount;

    /** 混合职级配置（JSON 字符串或后端自行拼接） */
    private String assumptions;

    @NotNull(message = "目标毛利率不能为空")
    private BigDecimal targetMargin;

    private String remark;
    private Long applicantId;
    private String applicantName;
}
