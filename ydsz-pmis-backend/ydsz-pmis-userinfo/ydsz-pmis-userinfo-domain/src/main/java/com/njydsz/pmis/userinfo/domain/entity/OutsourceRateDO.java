package com.njydsz.pmis.userinfo.domain.entity.rate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 外包职级费率实体（V1-V18，人天核算月薪+差旅报销+差旅补贴）
 *
 * <p>与全职 {@link RankRateDO}（L1-L18）和兼职 {@link PartTimeRateDO}（P1-P18）平行，
 * 用于外包员工的成本核算。外包核心计价单元为<strong>人天单价</strong>，
 * 月薪 = 人天单价(dailyRate) × 月工作天数(monthlyDays)。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_outsource_rate")
public class OutsourceRateDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 外包级别编码（V1-V18） */
    private String rateCode;

    /** 级别名称 */
    private String rateName;

    /** 级别段位：PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
    private String levelSegment;

    /** 人天单价（元/天，外包核心计价单元） */
    private BigDecimal dailyRate;

    /** 月工作天数（默认22天） */
    private BigDecimal monthlyDays;

    /** 月度薪资（元/月, = dailyRate × monthlyDays） */
    private BigDecimal monthlySalary;

    /** 差旅报销-公司承担部分（元/月） */
    private BigDecimal travelReimbursement;

    /** 差旅补贴-公司承担部分（元/月） */
    private BigDecimal travelAllowance;

    /** 公司总人力成本（元/月, = monthlySalary + travelReimbursement + travelAllowance） */
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
