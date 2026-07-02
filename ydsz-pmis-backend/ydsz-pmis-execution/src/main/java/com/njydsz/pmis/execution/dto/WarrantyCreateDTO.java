package com.njydsz.pmis.execution.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 质保期创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class WarrantyCreateDTO {
    private String warrantyCode;
    private Long initiationId;
    private Long contractId;
    private String projectType;
    private String projectLevel;
    private LocalDate startDate;
    private Integer durationMonths;
    private Integer noticeDays;
    private String contactName;
    private String contactPhone;
    private String remark;
}
