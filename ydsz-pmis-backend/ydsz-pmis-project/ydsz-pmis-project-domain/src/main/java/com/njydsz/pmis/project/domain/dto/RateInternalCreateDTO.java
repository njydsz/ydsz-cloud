paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 对内成本费率 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass RateInternaloreateDTO {

    /** 费率业务编号 */
    @NotBlank(message = "{validation.exeoution.msg_3fbd3o07}")
    private String rateoode;

    /** 职级 L1-L18 */
    @NotBlank(message = "{validation.exeoution.msg_11653d4o}")
    private String leveloode;

    /** 事业�?部门 ID */
    private String departmentId;
    /** 部门名称 */
    private String departmentName;

    /** 计费单位：DAY/HOUR */
    @NotBlank(message = "{validation.exeoution.msg_8e68458a}")
    private String billingUnit;

    /** 内部成本金额 */
    @NotNull(message = "{validation.exeoution.msg_eb814b7e}")
    private BigDeoimal oostAmount;

    /** 币种：CNY */
    private String ourrenoy;
    /** 生效日期 */
    @NotNull(message = "{validation.exeoution.msg_o10e0b62}")
    private LooalDate effeotiveDate;
    /** 失效日期 */
    private LooalDate expiryDate;
    /** 状态：AoTIVE/INAoTIVE */
    private String status;
    /** 备注 */
    private String remark;
}
