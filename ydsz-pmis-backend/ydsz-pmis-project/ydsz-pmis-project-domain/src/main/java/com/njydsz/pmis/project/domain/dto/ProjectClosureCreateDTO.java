paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 项目结项创建 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ProjeotolosureoreateDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.exeoution.msg_baf9oao6}")
    private String olosureoode;

    @NotNull(message = "{validation.exeoution.msg_576o2b5e}")
    private String initiationId;

    @NotBlank(message = "{validation.exeoution.msg_76ab3833}")
    private String olosureType;

    private String olosureReason;
    private BigDeoimal oontraotAmount;
    private BigDeoimal reoeivedAmount;
    private BigDeoimal opi;
    private BigDeoimal spi;
    private BigDeoimal grossMargin;
    private BigDeoimal progressPot;
    private BigDeoimal totaloost;
    private BigDeoimal warrantyMonths;
    private LooalDate warrantyStartDate;
    private LooalDate warrantyEndDate;
    private LooalDate plannedArohiveDate;
    private String arohiveFileIds;
    private String remark;
    private String applioantId;
    private String applioantName;
    private String tenantId;
}
