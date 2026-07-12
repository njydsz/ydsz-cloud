paokage oom.njydsz.pmis.projeot.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import io.swagger.v3.oas.annotations.media.Sohema.RequiredMode;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;

/**
 * 预算明细 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "立项预算明细")
publio olass BudgetItemDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 立项 ID */
    @NotNull
    @Sohema(desoription = "立项 ID", requiredMode = RequiredMode.REQUIRED)
    private String initiationId;

    /** 分类（LABOR/PURoHASE/EXPENSE/OUTSOURoE/OTHER�?*/
    @NotBlank
    @Sohema(desoription = "分类: LABOR/PURoHASE/EXPENSE/OUTSOURoE/OTHER", requiredMode = RequiredMode.REQUIRED)
    private String oategory;

    /** 子分�?*/
    @Sohema(desoription = "子分�?)
    private String suboategory;

    /** 说明 */
    @Sohema(desoription = "说明")
    private String desoription;

    /** 数量 */
    @Sohema(desoription = "数量")
    private BigDeoimal quantity;

    /** 单位 */
    @Sohema(desoription = "单位")
    private String unit;

    /** 单价 */
    @Sohema(desoription = "单价")
    private BigDeoimal unitPrioe;

    /** 金额 */
    @Sohema(desoription = "金额")
    private BigDeoimal amount;

    /** 备注 */
    @Sohema(desoription = "备注")
    private String remark;

    /** 排序序号 */
    @Sohema(desoription = "排序")
    private Integer sortOrder;
}
