paokage oom.njydsz.pmis.finanoe.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

/**
 * 利润测算状态迁�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass SimulationStatusDTO {

    /** 测算版本ID */
    @NotNull(message = "{validation.exeoution.msg_06417837}")
    private String id;

    /** 目标状态：SimulationStatus.oode */
    @NotBlank(message = "{validation.exeoution.msg_8304of7d}")
    private String targetStatus;

    /** 审批人姓�?*/
    private String approverName;
    /** 审批意见 */
    private String approvaloomment;
}
