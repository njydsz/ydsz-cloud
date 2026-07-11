package com.njydsz.pmis.finance.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 利润测算 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ProfitSimulationCreateDTO {

    /** 测算业务编号 */
    @NotBlank(message = "{validation.execution.msg_dd45c4cb}")
    private String simulationCode;

    /** 测算名称 */
    @NotBlank(message = "{validation.execution.msg_00a76083}")
    private String simulationName;

    /** 关联项目立项ID */
    @NotNull(message = "{validation.execution.msg_576c2b5e}")
    private String initiationId;

    /** 场景类型：BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM */
    private String scenarioType;      // BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM

    /** 合同金额 */
    @NotNull(message = "{validation.execution.msg_578c757b}")
    private BigDecimal contractAmount;

    /** 混合职级配置（JSON 字符串或后端自行拼接） */
    private String assumptions;

    /** 目标毛利率 */
    @NotNull(message = "{validation.execution.msg_3dd07a1f}")
    private BigDecimal targetMargin;

    /** 备注 */
    private String remark;
    /** 申请人ID */
    private String applicantId;
    /** 申请人姓名 */
    private String applicantName;
}
