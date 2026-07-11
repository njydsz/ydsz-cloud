package com.njydsz.pmis.userinfo.domain.entity.rate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 兼职职级费率实体（P1-P18，时薪核算月薪+商业保险）
 *
 * <p>与全职 {@link RankRateDO}（L1-L18，月薪+社保公积金）平行，
 * 用于兼职员工的成本核算。兼职核心计价单元为<strong>时薪</strong>，
 * 月薪 = 时薪(hourlyRate) × 月工时数(monthlyHours)。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_part_time_rate")
public class PartTimeRateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 兼职级别编码（P1-P18） */
    private String rateCode;

    /** 级别名称 */
    private String rateName;

    /** 级别段位：PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
    private String levelSegment;

    /** 时薪（元/小时，兼职核心计价单元） */
    private BigDecimal hourlyRate;

    /** 月工时数（默认176小时=22天×8小时） */
    private BigDecimal monthlyHours;

    /** 月度薪资（元/月, = hourlyRate × monthlyHours） */
    private BigDecimal monthlySalary;

    /** 商业保险-公司承担部分（元/月） */
    private BigDecimal commercialInsurance;

    /** 差旅报销-公司承担部分（元/月） */
    private BigDecimal travelReimbursement;

    /** 差旅补贴-公司承担部分（元/月） */
    private BigDecimal travelAllowance;

    /** 公司总人力成本（元/月, = monthlySalary + commercialInsurance + travelReimbursement + travelAllowance） */
    private BigDecimal totalCost;

    /** 对外人天单价（元/天，用于向客户报价） */
    private BigDecimal externalDaily;

    /** 对内人天成本（元/天，用于内部利润核算） */
    private BigDecimal internalDaily;

    /** 可计费利用率目标 (0-1) */
    private BigDecimal billableTarget;

    /** 排序序号 */
    private Integer sortOrder;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 失效日期（NULL 表示长期有效） */
    private LocalDate expireDate;

    /** 版本号 */
    private Integer version;

    /** 描述 */
    private String description;

    /** 状态：ACTIVE/INACTIVE */
    private String status;
}
