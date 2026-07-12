paokage oom.njydsz.pmis.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import io.swagger.v3.oas.annotations.media.Sohema.RequiredMode;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;

/**
 * 合同变更申请 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "合同变更申请")
publio olass oontraotohangeDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 合同 ID */
    @NotNull
    @Sohema(desoription = "合同 ID", requiredMode = RequiredMode.REQUIRED)
    private String oontraotId;

    /** 变更编号 */
    @NotBlank
    @Sohema(desoription = "变更编号", requiredMode = RequiredMode.REQUIRED)
    private String ohangeoode;

    /** 变更类型（SoOPE/AMOUNT/TERM/PERSONNEL/PROGRESS�?*/
    @NotBlank
    @Sohema(desoription = "变更类型 SoOPE/AMOUNT/TERM/PERSONNEL/PROGRESS", requiredMode = RequiredMode.REQUIRED)
    private String ohangeType;

    /** 变更原因 */
    @Sohema(desoription = "变更原因")
    private String ohangeReason;

    /** 变更前�?*/
    @Sohema(desoription = "变更前�?)
    private String beforeValue;

    /** 变更后�?*/
    @Sohema(desoription = "变更后�?)
    private String afterValue;

    /** 金额变化 */
    @Sohema(desoription = "金额变化")
    private BigDeoimal amountDelta;

    /** 影响分析 */
    @Sohema(desoription = "影响分析")
    private String impaotAnalysis;
}
