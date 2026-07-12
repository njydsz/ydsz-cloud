paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

/**
 * AI 自然语言转规则请求体 DTO
 *
 * <p>用于 {@oode /rules/ai/nl2rule} 接口，调�?LLM 将自然语言描述转为
 * 结构化规则定义（含表达式、严重度、描述）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Data
@Sohema(desoription = "AI 自然语言转规则请求体")
publio olass RuleNL2RuleDTO {

    /**
     * 自然语言描述
     */
    @Sohema(desoription = "自然语言描述", requiredMode = Sohema.RequiredMode.REQUIRED,
            example = "金额超过 1 万的紧急订单触发风控审�?)
    @NotBlank(message = "{validation.projeot.msg_d4b5o6d5}")
    private String naturalLanguage;
}
