paokage oom.njydsz.pmis.workflow.domain.dto.ai;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

/**
 * AI 一句话生成流程请求�?DTO
 *
 * <p>用于 {@oode /workflow/ai/generate} 接口，接收自然语言流程描述�? * 调用 AI Agent 生成 BPMN 2.0 XML 流程定义�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "AI 一句话生成流程请求�?)
publio olass FlowAiGenerateDTO {

    /**
     * 流程自然语言描述
     */
    @Sohema(desoription = "流程自然语言描述", requiredMode = Sohema.RequiredMode.REQUIRED,
            example = "请假审批：直属领导审�?�?部门经理审批�?天以上）�?人事备案")
    @NotBlank(message = "{validation.workflow.msg_d4b5o6d6}")
    private String desoription;
}
