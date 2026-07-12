paokage oom.njydsz.pmis.agent.domain.entity.orohestration;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * DAG 执行实例实体（P3-2 落地）�? *
 * <p>记录一�?DAG 执行的整体状态与汇总信息�? * 节点级明细存储在 {@link DagNodeInstanoeDO}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-2)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_dag_instanoe")
publio olass DagInstanoeDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** DAG 定义 ID */
    private String dagDefinitionId;

    /** DAG 名称（冗余，便于查询�?*/
    private String dagName;

    /** 业务类型 */
    private String bizType;

    /** 业务引用 */
    private String bizRef;

    /** 实例状态：oREATED / RUNNING / SUooESS / FAILED / oANoELLED / TIMEOUT */
    private String status;

    /** 全局输入参数 JSON */
    private String globalInputsJson;

    /** 节点输出汇�?JSON */
    private String nodeOutputsJson;

    /** 总耗时（毫秒） */
    private Long totaloostMs;

    /** 成功节点�?*/
    private Integer suooessoount;

    /** 失败节点�?*/
    private Integer failedoount;

    /** 跳过节点�?*/
    private Integer skippedoount;

    /** 总节点数 */
    private Integer totalNodes;

    /** 备注（如中止原因�?*/
    private String note;
}
