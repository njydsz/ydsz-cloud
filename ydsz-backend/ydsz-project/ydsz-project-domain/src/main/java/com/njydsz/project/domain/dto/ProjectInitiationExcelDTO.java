package com.njydsz.project.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.annotation.ExcelSheet;

import lombok.Data;

/**
 * 项目立项 Excel 导入导出 DTO。
 *
 * <p>使用 common-excel 注解驱动映射，支持 Excel 批量导入和导出。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
@ExcelSheet(name = "项目立项")
public class ProjectInitiationExcelDTO {

    @ExcelProperty(value = "项目编号", order = 1)
    private String projectCode;

    @ExcelProperty(value = "项目名称", order = 2)
    private String projectName;

    @ExcelProperty(value = "客户ID", order = 3)
    private String customerId;

    @ExcelProperty(value = "项目类型", order = 4)
    private String projectType;

    @ExcelProperty(value = "项目等级", order = 5)
    private String projectLevel;

    @ExcelProperty(value = "项目经理", order = 6)
    private String pmId;

    @ExcelProperty(value = "发起人", order = 7)
    private String sponsorId;

    @ExcelProperty(value = "预估金额", order = 8)
    private BigDecimal estimatedAmount;

    @ExcelProperty(value = "预算金额", order = 9)
    private BigDecimal budgetAmount;

    @ExcelProperty(value = "计划开始日期", order = 10, dateFormat = "yyyy-MM-dd")
    private LocalDate plannedStartDate;

    @ExcelProperty(value = "计划结束日期", order = 11, dateFormat = "yyyy-MM-dd")
    private LocalDate plannedEndDate;

    @ExcelProperty(value = "项目描述", order = 12)
    private String description;

    @ExcelProperty(value = "阶段", order = 13)
    private String stage;

    @ExcelProperty(value = "状态", order = 14)
    private String status;
}
