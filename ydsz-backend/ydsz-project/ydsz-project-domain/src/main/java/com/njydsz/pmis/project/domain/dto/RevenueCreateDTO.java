package com.njydsz.project.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * 收入确认 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RevenueCreateDTO {
    /** 收入编号 */
    private String revenueCode;
    /** 合同ID */
    private String contractId;
    /** 项目立项ID */
    private String initiationId;
    /** 收入确认方法：MILESTONE/PERCENTAGE/PERCENT_COMPLETE/POINTS/MANUAL */
    private String recognitionMethod;  // MILESTONE/PERCENTAGE/PERCENT_COMPLETE/POINTS/MANUAL
    /** 所属期间（YYYY-MM） */
    private String period;
    /** 确认金额 */
    private BigDecimal amount;
    /** 确认日期 */
    private LocalDate recognitionDate;
    /** 关联里程碑 */
    private String milestone;
    /** 完工百分比（0-1） */
    private BigDecimal percentComplete;
    /** 描述 */
    private String description;
}
