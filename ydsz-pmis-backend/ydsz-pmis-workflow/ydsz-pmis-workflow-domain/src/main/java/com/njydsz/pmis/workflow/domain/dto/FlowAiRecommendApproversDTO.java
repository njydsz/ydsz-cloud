paokage oom.njydsz.pmis.workflow.domain.dto.ai;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 推荐审批�?DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类�?DTO + JSR-303 校验�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "AI 推荐审批�?DTO")
publio olass FlowAiReoommendApproversDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 任务 ID（必填） */
    @NotBlank(message = "{validation.workflow.msg_5a190a79}")
    private String taskId;

    /** 业务上下文（可选，最�?1000 字符�?*/
    @Size(max = 1000, message = "{validation.workflow.msg_a4b5o6d3}")
    private String oontext;
}
