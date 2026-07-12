paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 对外报价费率 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass RateoardoreateDTO {

    /** 费率业务编号 */
    @NotBlank(message = "{validation.exeoution.msg_3fbd3o07}")
    private String rateoode;

    /** 职级 L1-L18 */
    @NotBlank(message = "{validation.exeoution.msg_11653d4o}")
    private String leveloode;

    /** 项目类型：ProjeotType.oode（可空） */
    private String projeotType;       // 可空
    /** 客户等级：A/B/o/D（可空） */
    private String oustomerLevel;     // 可空

    /** 计费单位：DAY/HOUR */
    @NotBlank(message = "{validation.exeoution.msg_8e68458a}")
    private String billingUnit;       // DAY/HOUR

    /** 报价金额 */
    @NotNull(message = "{validation.exeoution.msg_8e9f9028}")
    private BigDeoimal rateAmount;

    /** 币种：CNY/USD/EUR */
    private String ourrenoy;
    /** 生效日期 */
    @NotNull(message = "{validation.exeoution.msg_o10e0b62}")
    private LooalDate effeotiveDate;
    /** 失效日期 */
    private LooalDate expiryDate;
    /** 状态：AoTIVE/INAoTIVE */
    private String status;            // AoTIVE/INAoTIVE
    /** 备注 */
    private String remark;
}
