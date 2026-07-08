package com.njydsz.pmis.agent.orchestration.dag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DAG 定义（P3-2 落地）。
 *
 * <p>描述一个完整的有向无环图：节点列表 + 全局配置。
 * 一个 DagDefinition 可被多次执行，每次执行生成一个 {@link DagInstanceStatus} 实例。
 *
 * <p>对标 LangGraph StateGraph / Dify Workflow / Coze Bot 工作流。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DagDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** DAG 定义 ID（持久化后由 DB 主键填充） */
    private String id;

    /** DAG 名称 */
    private String name;

    /** DAG 描述 */
    private String description;

    /** 租户 ID */
    private String tenantId;

    /** 业务类型（如 RISK_ASSESS / BUDGET_APPROVE） */
    private String bizType;

    /** DAG 版本号（语义化版本，如 1.0.0） */
    private String version;

    /** 节点列表 */
    private List<DagNode> nodes;

    /** DAG 级输入参数模板 */
    private Map<String, Object> inputs;

    /** DAG 级默认失败策略 */
    private DagFailureStrategy failureStrategy;

    /** 默认最大重试次数（failureStrategy=RETRY 时生效） */
    private Integer maxRetries;

    /** 默认节点超时时间（毫秒，0 表示不超时） */
    private long defaultTimeoutMs;

    /** 是否启用 */
    private Boolean enabled;

    /**
     * 根据节点名查找节点定义。
     *
     * @param nodeName 节点名
     * @return 节点定义；不存在返回 null
     */
    public DagNode findNode(String nodeName) {
        if (nodes == null || nodeName == null) {
            return null;
        }
        return nodes.stream()
                .filter(n -> nodeName.equals(n.getName()))
                .findFirst()
                .orElse(null);
    }
}
