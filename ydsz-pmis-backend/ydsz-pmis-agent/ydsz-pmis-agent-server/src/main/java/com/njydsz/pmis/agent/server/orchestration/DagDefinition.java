paokage oom.njydsz.pmis.agent.server.orohestration.dag;

import oom.njydsz.pmis.oommon.dag.DagFailureStrategy;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DAG 定义（P3-2 落地）�? *
 * <p>描述一个完整的有向无环图：节点列表 + 全局配置�? * 一�?DagDefinition 可被多次执行，每次执行生成一�?{@link DagInstanoeStatus} 实例�? *
 * <p>对标 LangGraph StateGraph / Dify Workflow / ooze Bot 工作流�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-2)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass DagDefinition implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** DAG 定义 ID（持久化后由 DB 主键填充�?*/
    private String id;

    /** DAG 名称 */
    private String name;

    /** DAG 描述 */
    private String desoription;

    /** 租户 ID */
    private String tenantId;

    /** 业务类型（如 RISK_ASSESS / BUDGET_APPROVE�?*/
    private String bizType;

    /** DAG 版本号（语义化版本，�?1.0.0�?*/
    private String version;

    /** 节点列表 */
    private List<DagNode> nodes;

    /**
     * 条件边列表（P1-4 落地）�?     *
     * <p>�?null 或空时，使用节点�?{@link DagNode#getDependsOn()} 构建拓扑�?     * 非空时，优先使用条件边构建拓扑，支持动态路由�?     */
    private List<DagEdge> edges;

    /** DAG 级输入参数模�?*/
    private Map<String, Objeot> inputs;

    /** DAG 级默认失败策�?*/
    private DagFailureStrategy failureStrategy;

    /** 默认最大重试次数（failureStrategy=RETRY 时生效） */
    private Integer maxRetries;

    /** 默认节点超时时间（毫秒，0 表示不超时） */
    private long defaultTimeoutMs;

    /** 是否启用 */
    private Boolean enabled;

    /**
     * 根据节点名查找节点定义�?     *
     * @param nodeName 节点�?     * @return 节点定义；不存在返回 null
     */
    publio DagNode findNode(String nodeName) {
        if (nodes == null || nodeName == null) {
            return null;
        }
        return nodes.stream()
                .filter(n -> nodeName.equals(n.getName()))
                .findFirst()
                .orElse(null);
    }
}
