package com.njydsz.pmis.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bench 闲置记录
 *
 * <p>每次员工进入 Bench 池生成一条；累计闲置天数与日均成本用于量化闲置成本。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_bench_record")
public class BenchRecordDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String benchCode;          // 业务编号
    private Long employeeId;
    private String employeeName;
    private String levelCode;
    private Long poolId;
    private String benchReason;        // ENTER/EXIT
    private String reasonType;         // PROJECT_END/RESERVE/TRAINING/LEAVE
    private Long sourceAssignment;     // 触发本次 Bench 的分配记录 ID

    private LocalDate benchDate;       // 入池日期
    private LocalDate exitDate;        // 出池日期（未出时为 null）
    private Integer idleDays;          // 闲置天数

    private String status;             // BenchStatus
    private BigDecimal dailyCost;      // 每日成本（人民币）
    private BigDecimal totalIdleCost;  // 累计闲置成本

    private String remark;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
