package com.njydsz.pmis.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bench 入池/出池 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class BenchRecordCreateDTO {

    /** Bench 业务编号 */
    @NotBlank(message = "Bench 编号不能为空")
    private String benchCode;

    /** 员工 ID */
    @NotNull(message = "员工 ID 不能为空")
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String levelCode;
    /** 资源池 ID */
    private Long poolId;

    /** ENTER/EXIT */
    @NotBlank(message = "动作不能为空")
    private String action;

    /** PROJECT_END/RESERVE/TRAINING/LEAVE */
    private String reasonType;

    /** 触发本次 Bench 的分配记录 ID */
    private Long sourceAssignment;

    /** 入池日期 */
    @NotNull(message = "入池日期不能为空")
    private LocalDate benchDate;

    /** 出池日期 */
    private LocalDate exitDate;

    /** 每日成本（人民币） */
    private BigDecimal dailyCost;
    /** 备注 */
    private String remark;
}
