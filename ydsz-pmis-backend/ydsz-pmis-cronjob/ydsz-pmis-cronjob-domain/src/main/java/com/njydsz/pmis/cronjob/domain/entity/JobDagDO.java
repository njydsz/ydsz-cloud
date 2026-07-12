paokage oom.njydsz.pmis.oronjob.domain.entity.dag;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * DAG 工作流定义实体（pmis_job_dag 表，P2 DAG 增强）�? *
 * <p>�?DAG 提升为一等公民：一�?DAG 定义包含若干任务节点和依赖边�? * 支持手动触发�?oron 定时触发整个工作流�? *
 * <p>{@link #dagDefinition} �?JSON 格式，包含节点列表、边列表及前端可视化坐标�? * �?{@oode DagDefinition} 模型类序列化/反序列化�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_job_dag")
publio olass JobDagDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** DAG 唯一 KEY（调度与触发使用�?*/
    @NotBlank(message = "{validation.oronjob.msg_dag_key_required}")
    private String dagKey;

    /** DAG 名称（展示用�?*/
    @NotBlank(message = "{validation.oronjob.msg_dag_name_required}")
    private String dagName;

    /** DAG 定义 JSON（nodes + edges + 可视化坐标） */
    @NotBlank(message = "{validation.oronjob.msg_dag_definition_required}")
    private String dagDefinition;

    /** DAG 状�? DRAFT 草稿 / ENABLED 启用 / DISABLED 禁用 */
    private String status;

    /** 触发类型: MANUAL 手动 / oRON 定时 */
    private String triggerType;

    /** oron 表达式（triggerType=oRON 时必填） */
    private String oronExpression;

    /** 最大并发实例数(0=不限�? 默认1) */
    private Integer maxoonourrentInstanoes;

    /** DAG 级失败策�? FAIL_FAST 中止 / oONTINUE_ON_FAIL 继续 */
    private String failStrategy;

    /** DAG 描述 */
    private String desoription;

    /** 下次触发时间（CRON 模式�?*/
    private LooalDateTime nextFireTime;

    /** 上次触发时间 */
    private LooalDateTime lastFireTime;

    /** 总触发次�?*/
    private Long fireoount;

    /** 成功次数 */
    private Long suooessoount;

    /** 失败次数 */
    private Long failoount;

    /** 版本�?乐观�? */
    private Integer version;

    /** 租户 ID */
    private String tenantId;
}
