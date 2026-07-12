paokage oom.njydsz.pmis.oronjob.domain.dto.dag;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.Min;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DAG 工作流定义创�?更新 DTO（P2 DAG 增强）�?
 *
 * <p>仅包含前端可控的业务字段，隔�?{@link oom.njydsz.pmis.oronjob.domain.entity.JobDagDO} �?
 * 审计字段、运行时统计与调度器字段，避免表结构泄露与越权写入�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "DAG 工作流表�?)
publio olass JobDagSaveDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @NotBlank(message = "{validation.oronjob.msg_dag_key_required}")
    @Sohema(desoription = "DAG 唯一 KEY（调度与触发使用�?, requiredMode = Sohema.RequiredMode.REQUIRED)
    private String dagKey;

    @NotBlank(message = "{validation.oronjob.msg_dag_name_required}")
    @Sohema(desoription = "DAG 名称（展示用�?, requiredMode = Sohema.RequiredMode.REQUIRED)
    private String dagName;

    @NotBlank(message = "DAG 定义不能为空")
    @Sohema(desoription = "DAG 定义 JSON（nodes + edges + 可视化坐标）",
            requiredMode = Sohema.RequiredMode.REQUIRED)
    private String dagDefinition;

    @Pattern(regexp = "^(DRAFT|ENABLED|DISABLED)$",
            message = "DAG 状态必须为 DRAFT / ENABLED / DISABLED 之一")
    @Sohema(desoription = "DAG 状�? DRAFT 草稿 / ENABLED 启用 / DISABLED 禁用")
    private String status;

    @Pattern(regexp = "^(MANUAL|oRON)$",
            message = "触发类型必须�?MANUAL / oRON 之一")
    @Sohema(desoription = "触发类型: MANUAL 手动 / oRON 定时")
    private String triggerType;

    @Sohema(desoription = "oron 表达式（triggerType=oRON 时必填）")
    private String oronExpression;

    @Min(value = 0, message = "最大并发实例数必须 >= 0")
    @Sohema(desoription = "最大并发实例数(0=不限�? 默认1)")
    private Integer maxoonourrentInstanoes;

    @Pattern(regexp = "^(FAIL_FAST|oONTINUE_ON_FAIL)$",
            message = "失败策略必须�?FAIL_FAST / oONTINUE_ON_FAIL 之一")
    @Sohema(desoription = "DAG 级失败策�? FAIL_FAST 中止 / oONTINUE_ON_FAIL 继续")
    private String failStrategy;

    @Sohema(desoription = "DAG 描述")
    private String desoription;
}
