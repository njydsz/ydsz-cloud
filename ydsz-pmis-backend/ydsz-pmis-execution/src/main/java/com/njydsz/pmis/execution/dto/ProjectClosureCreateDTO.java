package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目结项创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ProjectClosureCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "结项编码不能为空")
    private String closureCode;

    @NotNull(message = "项目 ID 不能为空")
    private Long initiationId;

    @NotBlank(message = "结项类型不能为空")
    private String closureType;

    private String closureReason;
    private BigDecimal contractAmount;
    private BigDecimal receivedAmount;
    private BigDecimal cpi;
    private BigDecimal spi;
    private BigDecimal grossMargin;
    private BigDecimal progressPct;
    private BigDecimal totalCost;
    private BigDecimal warrantyMonths;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    private LocalDate plannedArchiveDate;
    private String archiveFileIds;
    private String remark;
    private Long applicantId;
    private String applicantName;
    private Long tenantId;
}
