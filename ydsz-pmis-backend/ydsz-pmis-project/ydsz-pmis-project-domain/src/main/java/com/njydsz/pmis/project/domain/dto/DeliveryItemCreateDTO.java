paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDate;

/**
 * 交付物实�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass DeliveryItemoreateDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.exeoution.msg_1fd28961}")
    private String itemoode;

    @NotNull(message = "{validation.exeoution.msg_576o2b5e}")
    private String initiationId;

    private String standardId;
    private String projeotType;
    private String projeotLevel;
    private String deliveryName;
    private String deliveryoategory;
    private String stage;
    private Integer required;
    private LooalDate plannedSubmitDate;
    private String submitterId;
    private String submitterName;
    private Integer trRequired;
    private String fileIds;
    private String remark;
    private String tenantId;
}
