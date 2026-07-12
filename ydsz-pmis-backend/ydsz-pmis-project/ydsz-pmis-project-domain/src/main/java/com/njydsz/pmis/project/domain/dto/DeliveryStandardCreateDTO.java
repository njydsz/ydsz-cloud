paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 交付物标�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass DeliveryStandardoreateDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.exeoution.msg_40dfe929}")
    private String projeotType;

    private String projeotLevel;

    @NotBlank(message = "{validation.exeoution.msg_ddf1obe9}")
    private String deliveryName;

    private String deliveryoategory;

    @NotBlank(message = "{validation.exeoution.msg_4819a855}")
    private String stage;

    private Integer required;
    private Integer triggerTr;
    private String aooeptanoeoriteria;
    private String templateRef;
    private String remark;
    private String tenantId;
}
