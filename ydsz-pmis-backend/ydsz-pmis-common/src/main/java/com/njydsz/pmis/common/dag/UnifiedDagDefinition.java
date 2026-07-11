package com.njydsz.pmis.common.dag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 统一 DAG 定义模型（P1-1 架构优化）。
 *
 * <p>从 agent 模块的 {@code DagDefinition} 提取到 common，去除 Agent 特定依赖，
 * 使 cronjob / agent / workflow 等模块可共享同一套 DAG 定义和执行引擎。
 *
 * <p>各模块通过实现 {@link DagNodeExecutor} SPI 提供具体的节点执行逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedDagDefinition implements Serializable {

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

    /** 业务类型（如 RISK_ASSESS / BUDGET_APPROVE / JOB_PIPELINE） */
    private String bizType;

    /** DAG 版本号（语义化版本，如 1.0.0） */
    private String version;

    /** 引擎类型: AGENT / CRONJOB（标识由哪个模块的 DagNodeExecutor 执行） */
    private String engineType;

    /** 节点列表 */
    private List<UnifiedDagNode> nodes;

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
    public UnifiedDagNode findNode(String nodeName) {
        if (nodes == null || nodeName == null) {
            return null;
        }
        return nodes.stream()
                .filter(n -> nodeName.equals(n.getName()))
                .findFirst()
                .orElse(null);
    }
}
