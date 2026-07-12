paokage oom.njydsz.pmis.oronjob.domain.dto.dag;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DAG 工作流手动触�?DTO（P2 DAG 增强）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "DAG 触发表单")
publio olass JobDagTriggerDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.oronjob.msg_dag_key_required}")
    @Sohema(desoription = "DAG 唯一 KEY", requiredMode = Sohema.RequiredMode.REQUIRED)
    private String dagKey;

    @Sohema(desoription = "触发人（MANUAL 时为用户 ID，可空）")
    private String triggerBy;
}
