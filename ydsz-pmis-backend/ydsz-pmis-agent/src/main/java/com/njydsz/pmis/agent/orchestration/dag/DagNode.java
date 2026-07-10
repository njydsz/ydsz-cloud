package com.njydsz.pmis.agent.orchestration.dag;

import com.njydsz.pmis.common.dag.DagFailureStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DAG 节点定义（P3-2 落地）。
 *
 * <p>一个节点代表 DAG 中的一个执行单元，对应一个 Agent 调用或一个子编排。
 * 节点间的依赖关系通过 {@link #dependsOn} 表达（入边），简化模型避免额外的 Edge 类。
 *
 * <p>对标 LangGraph Node / Dify Node / Coze 工作流节点。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DagNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点名称（DAG 内唯一标识）。
     *
     * <p>同时作为节点 ID，被其他节点的 {@link #dependsOn} 引用。
     */
    private String name;

    /**
     * 节点显示名称（可选，用于 UI 展示）。
     */
    private String displayName;

    /**
     * 关联的 Agent 类型（agentType）。
     *
     * <p>执行时通过 {@code Map<String, Agent> agents} 查找具体 Agent 实例。
     * 为 null 时表示空节点（仅做流程编排，不调用 Agent）。
     */
    private String agentType;

    /**
     * 依赖的前置节点名称列表（入边）。
     *
     * <p>为空或 null 表示起始节点（无入边）。
     * 所有前置节点必须 SUCCESS 才会调度本节点。
     */
    private List<String> dependsOn;

    /**
     * 条件表达式（可选，SpEL 语法）。
     *
     * <p>当表达式求值为 false 时，本节点被标记为 SKIPPED，其下游也递归跳过。
     * 表达式上下文为 {@link DagExecutionContext} 的共享变量。
     * 为 null 表示无条件执行。
     */
    private String condition;

    /**
     * 节点级别输入参数（会合并到执行上下文）。
     *
     * <p>支持引用上游节点输出：{@code #{upstreamNodeName}} 或 {@code #{upstreamNodeName.field}}。
     */
    private Map<String, Object> inputs;

    /**
     * 节点超时时间（毫秒，0 表示不超时）。
     *
     * <p>超时后节点标记为 FAILED，按失败策略处理。
     */
    private long timeoutMs;

    /**
     * 节点级失败策略（覆盖 DAG 级默认策略）。
     *
     * <p>为 null 时使用 {@link DagDefinition#getFailureStrategy()}。
     */
    private DagFailureStrategy failureStrategy;

    /**
     * 节点级最大重试次数（仅当 failureStrategy=RETRY 生效）。
     *
     * <p>为 null 时使用 DAG 默认值。
     */
    private Integer maxRetries;
}
