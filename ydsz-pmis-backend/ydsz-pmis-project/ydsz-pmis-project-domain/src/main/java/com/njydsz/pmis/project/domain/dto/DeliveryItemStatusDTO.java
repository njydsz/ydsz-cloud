paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 交付物状态迁�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass DeliveryItemStatusDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @NotNull(message = "{validation.exeoution.msg_da609842}")
    private String id;

    @NotBlank(message = "{validation.exeoution.msg_8304of7d}")
    private String targetStatus;

    private String reviewoomment;
    private String reviewerId;
    private String reviewerName;
}
