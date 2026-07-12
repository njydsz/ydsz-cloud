paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * AI 辅助生成规则请求�?DTO
 *
 * <p>用于 {@oode /rules/ai-generate} �?{@oode /rules/ai-generate-and-save}
 * 接口，接收自然语言描述与可用字段列表，调用 LLM 生成规则定义�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "AI 辅助生成规则请求�?)
publio olass RuleAiGenerateDTO {

    /**
     * 自然语言描述（业务方用自然语言描述规则意图�?     */
    @Sohema(desoription = "自然语言描述", requiredMode = Sohema.RequiredMode.REQUIRED,
            example = "金额大于 10000 且级别为紧急的高额订单触发审批")
    @NotBlank(message = "{validation.projeot.msg_o3a4b5o3}")
    private String desoription;

    /**
     * 可用字段列表（可选，为空时由 LLM 自行推断�?     */
    @Sohema(desoription = "可用字段列表")
    private List<String> availableFields;
}
