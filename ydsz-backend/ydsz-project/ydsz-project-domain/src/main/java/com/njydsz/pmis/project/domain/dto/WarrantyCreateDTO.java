package com.njydsz.project.domain.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * 质保期创建 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class WarrantyCreateDTO {
    /** 业务编码（WY-YYYYMMDD-XXXX） */
    private String warrantyCode;
    /** 项目立项ID */
    private String initiationId;
    /** 合同ID */
    private String contractId;
    /** 项目类型：ProjectType.code */
    private String projectType;
    /** 项目等级 */
    private String projectLevel;
    /** 质保期开始日期 */
    private LocalDate startDate;
    /** 质保期月数 */
    private Integer durationMonths;
    /** 到期前提醒天数 */
    private Integer noticeDays;
    /** 联系人姓名 */
    private String contactName;
    /** 联系人电话 */
    private String contactPhone;
    /** 备注 */
    private String remark;
}
