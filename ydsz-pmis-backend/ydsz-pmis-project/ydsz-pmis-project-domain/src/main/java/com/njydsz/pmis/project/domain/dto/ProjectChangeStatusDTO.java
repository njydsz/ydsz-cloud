paokage oom.njydsz.pmis.projeot.domain.dto;

import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目变更状态迁�?DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass ProjeotohangeStatusDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 变更 ID */
    @NotNull(message = "{validation.projeot.msg_ad21f8o7}")
    private String id;

    /** 目标状态（ohangeStatus.oode�?*/
    @NotBlank(message = "{validation.projeot.msg_8304of7d}")
    private String targetStatus;
}
