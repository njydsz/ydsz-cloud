package com.njydsz.pmis.project.entity;

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
 * 可计费利用率快照
 *
 * <p>由 BillableUtilizationJobHandler 定时（每日凌晨）计算并写入；
 * Cockpit 驾驶舱 / 排行榜 / 趋势分析 均直接读本表，避免每次实时聚合 pmis_execution_time_entry 大表。
 *
 * <p>键设计：(period, employee_id) 唯一，由 UPSERT 保证幂等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_billable_utilization_snapshot")
public class BillableUtilizationSnapshotDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 统计周期 yyyy-MM */
    private String period;

    /** 员工 ID */
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;

    /** 职级 */
    private String levelCode;

    /** 部门（来自 RateInternal） */
    private String department;

    /** 全部工时（含加班 / 请假 / 培训） */
    private BigDecimal totalHours;

    /** 可计费工时 */
    private BigDecimal billableHours;

    /** 加班工时 */
    private BigDecimal overtimeHours;

    /** 请假工时 */
    private BigDecimal leaveHours;

    /** 培训工时 */
    private BigDecimal trainingHours;

    /** 闲置（bench）工时 = total - billable - leave - training */
    private BigDecimal benchHours;

    /** 利用率 0-1 */
    private BigDecimal utilizationPct;

    /** 考核等级：EXCELLENT/GOOD/NORMAL/WARN/CRITICAL */
    private String grade;

    /** 区间起始 */
    private LocalDate rangeFrom;

    /** 区间截止 */
    private LocalDate rangeTo;

    /** 快照生成时间 */
    private LocalDateTime snapshotAt;

    /** 触发来源：CRONJOB / MANUAL / RETRO */
    private String source;

    /** 租户ID */
    private Long tenantId;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
