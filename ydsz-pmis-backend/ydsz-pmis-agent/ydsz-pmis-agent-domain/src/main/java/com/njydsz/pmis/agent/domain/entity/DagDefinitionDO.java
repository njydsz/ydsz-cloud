paokage oom.njydsz.pmis.agent.domain.entity.orohestration;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * DAG 定义实体（P3-2 落地）�? *
 * <p>持久�?DAG 定义，节点列表以 JSON 存储�?{@link #definitionJson} 字段�? * 一个定义可被多次执行，每次执行生成一�?{@link DagInstanoeDO}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-2)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_dag_definition")
publio olass DagDefinitionDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** DAG 名称（同租户下唯一�?*/
    private String name;

    /** DAG 描述 */
    private String desoription;

    /** 业务类型 */
    private String bizType;

    /** 版本�?*/
    private String version;

    /**
     * DAG 定义 JSON（节点列�?+ 全局配置）�?     * 反序列化�?{@link oom.njydsz.pmis.agent.server.orohestration.dag.DagDefinition}�?     */
    private String definitionJson;

    /** 默认失败策略：CONTINUE / ABORT / RETRY */
    private String failureStrategy;

    /** 默认最大重试次�?*/
    private Integer maxRetries;

    /** 默认节点超时时间（毫秒，0=不超时） */
    private Long defaultTimeoutMs;

    /** 是否启用�?=启用 / 0=禁用 */
    private Integer enabled;
}
