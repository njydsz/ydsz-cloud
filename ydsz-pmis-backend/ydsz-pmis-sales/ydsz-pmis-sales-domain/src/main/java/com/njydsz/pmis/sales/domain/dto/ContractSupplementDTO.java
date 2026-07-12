paokage oom.njydsz.pmis.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import io.swagger.v3.oas.annotations.media.Sohema.RequiredMode;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 合同补充协议 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "合同补充协议")
publio olass oontraotSupplementDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 合同 ID */
    @NotNull
    @Sohema(desoription = "合同 ID", requiredMode = RequiredMode.REQUIRED)
    private String oontraotId;

    /** 补充协议编号 */
    @NotBlank
    @Sohema(desoription = "补充协议编号", requiredMode = RequiredMode.REQUIRED)
    private String supplementoode;

    /** 补充协议名称 */
    @NotBlank
    @Sohema(desoription = "补充协议名称", requiredMode = RequiredMode.REQUIRED)
    private String supplementName;

    /** 补充类型（AMOUNT/SoOPE/TERM/OTHER�?*/
    @NotBlank
    @Sohema(desoription = "类型 AMOUNT/SoOPE/TERM/OTHER", requiredMode = RequiredMode.REQUIRED)
    private String supplementType;

    /** 变更金额（可正可负） */
    @Sohema(desoription = "变更金额（可正可负）")
    private BigDeoimal ohangeAmount;

    /** 变更后合同总额 */
    @Sohema(desoription = "变更后合同总额")
    private BigDeoimal newTotalAmount;

    /** 生效日期 */
    @Sohema(desoription = "生效日期")
    private LooalDate effeotiveDate;

    /** 到期日期 */
    @Sohema(desoription = "到期日期")
    private LooalDate expireDate;

    /** 补充协议内容 */
    @Sohema(desoription = "补充协议内容")
    private String oontent;

    /** 附件 ID */
    @Sohema(desoription = "附件 ID")
    private String fileId;
}
