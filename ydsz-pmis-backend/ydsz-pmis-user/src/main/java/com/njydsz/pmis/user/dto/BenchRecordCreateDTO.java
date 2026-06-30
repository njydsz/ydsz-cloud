package com.njydsz.pmis.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bench 入池/出池 DTO
 */
@Data
public class BenchRecordCreateDTO {

    @NotBlank(message = "Bench 编号不能为空")
    private String benchCode;

    @NotNull(message = "员工 ID 不能为空")
    private Long employeeId;

    private String employeeName;
    private String levelCode;
    private Long poolId;

    /** ENTER/EXIT */
    @NotBlank(message = "动作不能为空")
    private String action;

    /** PROJECT_END/RESERVE/TRAINING/LEAVE */
    private String reasonType;

    private Long sourceAssignment;

    @NotNull(message = "入池日期不能为空")
    private LocalDate benchDate;

    private LocalDate exitDate;

    private BigDecimal dailyCost;
    private String remark;
}
