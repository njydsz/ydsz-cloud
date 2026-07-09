package com.njydsz.pmis.userinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 兼职工时单价更新 DTO（部分更新，仅非空字段生效）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "兼职工时单价更新")
public class PartTimeRateUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 兼职级别编码 (P1-P18) */
    @Size(max = 8)
    private String rateCode;

    /** 级别名称 */
    @Size(max = 64)
    private String rateName;

    /** 工作日时薪 (元/小时) */
    private BigDecimal hourlyRate;

    /** 加班时薪 (元/小时, 可空) */
    private BigDecimal overtimeRate;

    /** 周末时薪 (元/小时, 可空) */
    private BigDecimal weekendRate;

    /** 法定节假日时薪 (元/小时, 可空) */
    private BigDecimal holidayRate;

    /** 级别段位: PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
    private String segment;

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
