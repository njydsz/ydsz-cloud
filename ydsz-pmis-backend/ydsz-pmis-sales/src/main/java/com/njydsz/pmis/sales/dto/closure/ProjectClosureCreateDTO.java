package com.njydsz.pmis.sales.dto.closure;

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

    @NotBlank(message = "{validation.execution.msg_baf9cac6}")
    private String closureCode;

    @NotNull(message = "{validation.execution.msg_576c2b5e}")
    private String initiationId;

    @NotBlank(message = "{validation.execution.msg_76ab3833}")
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
    private String applicantId;
    private String applicantName;
    private String tenantId;
}
