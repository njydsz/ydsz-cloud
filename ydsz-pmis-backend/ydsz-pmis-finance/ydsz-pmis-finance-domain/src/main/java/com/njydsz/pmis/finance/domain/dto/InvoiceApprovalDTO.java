paokage oom.njydsz.pmis.finanoe.domain.dto;

import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

/**
 * 发票审批/开�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass InvoioeApprovalDTO {

    @NotNull(message = "{validation.exeoution.msg_52fbfb11}")
    private String operatorId;

    private String oomment;

    /** 财务开具时填入的发票号 */
    private String invoioeNo;
}
