package com.njydsz.pmis.userinfo.dto.rate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 外包职级费率更新 DTO（部分更新，仅非空字段生效）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "外包职级费率更新")
public class OutsourceRateUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 外包级别编码 (V1-V18) */
    @Size(max = 8)
    private String rateCode;

    /** 级别名称 */
    @Size(max = 64)
    private String rateName;

    /** 级别段位: PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
    private String levelSegment;

    /** 人天单价 (元/天, 外包核心计价单元) */
    private BigDecimal dailyRate;

    /** 月工作天数 (默认22天) */
    private BigDecimal monthlyDays;

    /** 月度薪资 (元/月, = dailyRate × monthlyDays, 服务端自动计算) */
    private BigDecimal monthlySalary;

    /** 差旅报销-公司承担部分 (元/月) */
    private BigDecimal travelReimbursement;

    /** 差旅补贴-公司承担部分 (元/月) */
    private BigDecimal travelAllowance;

    /** 公司总人力成本 (元/月) */
    private BigDecimal totalCost;

    /** 对外人天单价 (元/天) */
    private BigDecimal externalDaily;

    /** 对内人天成本 (元/天) */
    private BigDecimal internalDaily;

    /** 可计费利用率目标 (0-1) */
    private BigDecimal billableTarget;

    /** 排序序号 */
    private Integer sortOrder;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 失效日期 (NULL 表示长期有效) */
    private LocalDate expireDate;

    /** 版本号 */
    private Integer version;

    /** 级别说明 */
    private String description;

    /** 状态: ACTIVE/INACTIVE */
    private String status;
}
