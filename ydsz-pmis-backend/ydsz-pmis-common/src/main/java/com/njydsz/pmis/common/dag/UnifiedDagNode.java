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
 * 统一 DAG 节点定义（P1-1 架构优化）。
 *
 * <p>从 agent 模块的 {@code DagNode} 提取到 common，去除 Agent 特定依赖。
 * 节点类型由 {@link #nodeType} 标识，由 {@link DagNodeExecutor} 实现方解析。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedDagNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 节点名称（DAG 内唯一标识） */
    private String name;

    /** 节点显示名称（可选，用于 UI 展示） */
    private String displayName;

    /** 节点类型（agent 的 agentType / cronjob 的 jobKey） */
    private String nodeType;

    /** 依赖的前置节点名称列表（入边），为空表示起始节点 */
    private List<String> dependsOn;

    /** 条件表达式（可选，SpEL 语法），为 null 表示无条件执行 */
    private String condition;

    /** 节点级别输入参数（会合并到执行上下文） */
    private Map<String, Object> inputs;

    /** 节点超时时间（毫秒，0 表示不超时） */
    private long timeoutMs;

    /** 节点级失败策略（覆盖 DAG 级默认策略） */
    private DagFailureStrategy failureStrategy;

    /** 节点级最大重试次数（仅当 failureStrategy=RETRY 生效） */
    private Integer maxRetries;
}
