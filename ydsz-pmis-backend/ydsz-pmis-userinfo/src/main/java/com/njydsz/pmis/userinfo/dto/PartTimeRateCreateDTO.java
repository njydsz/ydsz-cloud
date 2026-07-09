package com.njydsz.pmis.userinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 兼职职级费率创建 DTO（月薪+商业保险）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "兼职职级费率创建")
public class PartTimeRateCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 兼职级别编码 (P1-P18) */
    @NotBlank
    @Size(max = 8)
    private String rateCode;

    /** 级别名称 */
    @NotBlank
    @Size(max = 64)
    private String rateName;

    /** 级别段位: PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
    @NotBlank
    private String levelSegment;

    /** 月度薪资 (元/月) */
    @NotNull
    private BigDecimal monthlySalary;

    /** 商业保险-公司承担部分 (元/月) */
    private BigDecimal commercialInsurance;

    /** 公司总人力成本 (元/月, = monthlySalary + commercialInsurance) */
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
    @NotNull
    private LocalDate effectiveDate;

    /** 失效日期 (NULL 表示长期有效) */
    private LocalDate expireDate;

    /** 版本号 (为空时默认 1) */
    private Integer version;

    /** 级别说明 */
    private String description;

    /** 状态: ACTIVE/INACTIVE (为空时默认 ACTIVE) */
    private String status;
}
