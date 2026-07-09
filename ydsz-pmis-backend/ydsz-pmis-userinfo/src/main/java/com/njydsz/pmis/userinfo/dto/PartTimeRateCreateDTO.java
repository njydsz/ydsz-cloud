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
 * 兼职工时单价创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "兼职工时单价创建")
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

    /** 工作日时薪 (元/小时) */
    @NotNull
    private BigDecimal hourlyRate;

    /** 加班时薪 (元/小时, 可空) */
    private BigDecimal overtimeRate;

    /** 周末时薪 (元/小时, 可空) */
    private BigDecimal weekendRate;

    /** 法定节假日时薪 (元/小时, 可空) */
    private BigDecimal holidayRate;

    /** 级别段位: PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
    @NotBlank
    private String segment;

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
