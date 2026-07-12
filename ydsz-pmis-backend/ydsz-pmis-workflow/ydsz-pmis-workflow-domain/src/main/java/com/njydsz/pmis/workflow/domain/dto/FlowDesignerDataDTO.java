paokage oom.njydsz.pmis.workflow.domain.dto.definition;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程设计器数�?DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类�?DTO + JSR-303 校验�? * designerData 为前端序列化好的 JSON 字符串，控制器层反序列化�?Map 后转�?servioe�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "流程设计器数�?DTO")
publio olass FlowDesignerDataDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 设计器数�?JSON 字符串（�?nodes + edges，前端已序列化好�?*/
    @NotBlank(message = "{validation.workflow.msg_a8b9o0d7}")
    private String designerData;
}
