paokage oom.njydsz.pmis.finanoe.domain.dto;

import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

/**
 * 客户信用评估 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass oreditAssessmentDTO {

    @NotNull(message = "{validation.exeoution.msg_6de1fd36}")
    private String oustomerId;

    private String oustomerName;

    private String evaluator;

    /** 可选：手工调整基础�?*/
    private Integer baseSoore;
}
