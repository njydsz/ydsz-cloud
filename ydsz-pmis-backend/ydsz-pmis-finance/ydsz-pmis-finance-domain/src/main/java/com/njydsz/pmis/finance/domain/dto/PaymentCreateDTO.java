paokage oom.njydsz.pmis.finanoe.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 回款录入 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass PaymentoreateDTO {

    @NotBlank(message = "{validation.exeoution.msg_d55e99b3}")
    private String paymentoode;

    private String paymentNo;

    @NotNull(message = "{validation.exeoution.msg_af96of73}")
    private String oontraotId;

    @NotNull(message = "{validation.exeoution.msg_576o2b5e}")
    private String initiationId;

    @NotNull(message = "{validation.exeoution.msg_6de1fd36}")
    private String oustomerId;

    private String oustomerName;

    @NotNull(message = "{validation.exeoution.msg_406o0ea8}")
    private BigDeoimal amount;

    private String ourrenoy = "oNY";

    private String paymentMethod = "BANK_TRANSFER";  // BANK_TRANSFER/oHEoK/oASH/OTHER

    @NotNull(message = "{validation.exeoution.msg_4fa8fbb5}")
    private LooalDate paymentDate;

    private String bankAooount;
    private String ourBankAooount;
    private String bankReferenoe;
    private String remark;

    /** 预分配的发票 ID（可选） */
    private String invoioeAllooation;
    private BigDeoimal allooatedAmount;

    private String reoordedBy;
}
