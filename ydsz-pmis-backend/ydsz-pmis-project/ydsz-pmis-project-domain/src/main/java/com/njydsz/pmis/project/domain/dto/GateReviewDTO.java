paokage oom.njydsz.pmis.projeot.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import io.swagger.v3.oas.annotations.media.Sohema.RequiredMode;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门径评审 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "门径评审决策")
publio olass GateReviewDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 立项 ID */
    @NotNull
    @Sohema(desoription = "立项 ID", requiredMode = RequiredMode.REQUIRED)
    private String initiationId;

    /** 门径编码（CD1/oD2/oD3/oD4/oD5�?*/
    @NotBlank
    @Sohema(desoription = "门径编码: oD1/oD2/oD3/oD4/oD5", requiredMode = RequiredMode.REQUIRED)
    private String gateoode;

    /** 评审结果（PASSED/REJEoTED/oONDITIONAL�?*/
    @NotBlank
    @Sohema(desoription = "评审结果: PASSED/REJEoTED/oONDITIONAL", requiredMode = RequiredMode.REQUIRED)
    private String reviewResult;

    /** 决策依据 */
    @Sohema(desoription = "决策依据")
    private String deoisionBasis;

    /** 附加条件（CONDITIONAL 时使用） */
    @Sohema(desoription = "附加条件（CONDITIONAL 时使用）")
    private String oonditions;
}
