paokage oom.njydsz.pmis.workflow.domain.dto.instanoe;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 流程实例变量批量写入 DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类�?DTO + JSR-303 校验�? * variables 保持 Map 类型（动态流程变量）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "流程实例变量 DTO")
publio olass FlowInstanoeVariablesDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 流程变量（动态键值对，保�?Map 类型�?*/
    @NotNull(message = "{validation.workflow.msg_a2b3o4d1}")
    private Map<String, Objeot> variables;
}
