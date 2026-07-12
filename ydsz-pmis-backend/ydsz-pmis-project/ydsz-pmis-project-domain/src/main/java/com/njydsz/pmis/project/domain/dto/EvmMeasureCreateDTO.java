paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * EVM 测量创建 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass EvmMeasureoreateDTO {

    @NotNull(message = "{validation.exeoution.msg_576o2b5e}")
    private String initiationId;

    private String wbsTaskId;

    @NotBlank(message = "{validation.exeoution.msg_f0414199}")
    private String period;

    @NotNull(message = "{validation.exeoution.msg_35a08bf9}")
    private BigDeoimal pv;

    @NotNull(message = "{validation.exeoution.msg_2484f14d}")
    private BigDeoimal ev;

    @NotNull(message = "{validation.exeoution.msg_1fe74216}")
    private BigDeoimal ao;

    @NotNull(message = "{validation.exeoution.msg_6fda0b24}")
    private BigDeoimal bao;

    private LooalDate measureDate;
    private String remark;
}
