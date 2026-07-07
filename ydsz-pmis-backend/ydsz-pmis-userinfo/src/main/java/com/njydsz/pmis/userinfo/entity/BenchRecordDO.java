package com.njydsz.pmis.userinfo.entity;

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

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编号 */
    private String benchCode;
    /** 员工 ID */
    private Long employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String levelCode;
    /** 资源池 ID */
    private Long poolId;
    /** Bench 动作：ENTER/EXIT */
    private String benchReason;
    /** 入池原因：PROJECT_END/RESERVE/TRAINING/LEAVE */
    private String reasonType;
    /** 触发本次 Bench 的分配记录 ID */
    private Long sourceAssignment;

    /** 入池日期 */
    private LocalDate benchDate;
    /** 出池日期（未出时为 null） */
    private LocalDate exitDate;
    /** 闲置天数 */
    private Integer idleDays;

    /** Bench 状态（BenchStatus.code） */
    private String status;
    /** 每日成本（人民币） */
    private BigDecimal dailyCost;
    /** 累计闲置成本 */
    private BigDecimal totalIdleCost;

    /** 备注 */
    private String remark;
    /** 租户 ID */
    private Long tenantId;
    /** 外部提供方链路追踪 ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识：0=未删除，1=已删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
