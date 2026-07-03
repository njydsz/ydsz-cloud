package com.njydsz.pmis.project.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 对外报价费率卡（Rate Card）批量导入 DTO
 *
 * <p>对应 pmis_rate_card 表，模板由 {@code GET /api/v1/execution/import/template/rate-card} 下载。
 * 必填字段：level / customerType / projectType / unitPrice / effectiveDate
 * 可选字段：idempotencyKey（幂等键，空则按 (level+customerType+projectType+effectiveDate) 哈希生成）
 *
 * <p>导入流程：
 *   1. Controller 接收 MultipartFile → ExcelUtil.readStreaming 解析为本 DTO 列表
 *   2. Service 层逐行调用 RateCardService.create
 *   3. 失败行记录到导入日志表，前端可下载错误清单
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@ColumnWidth(20)
public class RateCardImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 职级 L1-L18 */
    @NotBlank(message = "{validation.execution.msg_11653d4c}")
    @ExcelProperty(value = "职级", index = 0)
    @ColumnWidth(12)
    private String level;

    /** 客户类型：GOV/ENT/SMB/INDIVIDUAL */
    @NotBlank(message = "{validation.execution.msg_d5cd6e50}")
    @ExcelProperty(value = "客户类型", index = 1)
    @ColumnWidth(16)
    private String customerType;

    /** 项目类型：FIXED_PRICE/T&M/MILESTONE/RETAINER/LICENSE/SaaS/MAINTENANCE/OTHER */
    @NotBlank(message = "{validation.execution.msg_40dfe929}")
    @ExcelProperty(value = "项目类型", index = 2)
    @ColumnWidth(20)
    private String projectType;

    /** 单价（元/人天） */
    @NotNull(message = "{validation.execution.msg_d1b0b464}")
    @ExcelProperty(value = "单价(元/人天)", index = 3)
    @NumberFormat("#.##")
    @ColumnWidth(18)
    private BigDecimal unitPrice;

    /** 生效日期 yyyy-MM-dd */
    @NotBlank(message = "{validation.execution.msg_c10e0b62}")
    @ExcelProperty(value = "生效日期", index = 4)
    @ColumnWidth(16)
    private String effectiveDate;

    /** 失效日期 yyyy-MM-dd（可空=长期） */
    @ExcelProperty(value = "失效日期", index = 5)
    @ColumnWidth(16)
    private String expiryDate;

    /** 币种，默认 CNY */
    @ExcelProperty(value = "币种", index = 6)
    @ColumnWidth(10)
    private String currency = "CNY";

    /** 备注 */
    @ExcelProperty(value = "备注", index = 7)
    @ColumnWidth(30)
    private String remark;
}
