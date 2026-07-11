package com.njydsz.pmis.userinfo.domain.dto.resource;

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
    @NotBlank(message = "{validation.user.msg_b0695d8f}")
    private String benchCode;

    /** 员工 ID */
    @NotNull(message = "{validation.user.msg_03f5ae35}")
    private String employeeId;

    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String levelCode;
    /** 资源池 ID */
    private String poolId;

    /** ENTER/EXIT */
    @NotBlank(message = "{validation.user.msg_f0494194}")
    private String action;

    /** PROJECT_END/RESERVE/TRAINING/LEAVE */
    private String reasonType;

    /** 触发本次 Bench 的分配记录 ID */
    private String sourceAssignment;

    /** 入池日期 */
    @NotNull(message = "{validation.user.msg_17fc001d}")
    private LocalDate benchDate;

    /** 出池日期 */
    private LocalDate exitDate;

    /** 每日成本（人民币） */
    private BigDecimal dailyCost;
    /** 备注 */
    private String remark;
}
