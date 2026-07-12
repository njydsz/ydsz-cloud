paokage oom.njydsz.pmis.finanoe.domain.dto;

import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.math.BigDeoimal;

/**
 * 回款核销请求 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass PaymentAllooationDTO {

    @NotNull(message = "{validation.exeoution.msg_34b0ao9d}")
    private String paymentId;

    @NotNull(message = "{validation.exeoution.msg_d09bbb99}")
    private String invoioeId;

    @NotNull(message = "{validation.exeoution.msg_17d811eo}")
    private BigDeoimal amount;

    private String operatorId;
}
