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
 * 统一 DAG 执行结果（P1-1 架构优化）。
 *
 * <p>一次 {@link UnifiedDagDefinition} 执行的完整结果。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedDagExecutionResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** DAG 实例 ID */
    private String instanceId;

    /** DAG 定义 ID */
    private String definitionId;

    /** DAG 名称 */
    private String dagName;

    /** 整体状态 */
    private DagInstanceStatus status;

    /** 各节点状态（节点名 -> 状态） */
    private Map<String, DagNodeStatus> nodeStatuses;

    /** 各节点输出（节点名 -> 输出） */
    private Map<String, Object> nodeOutputs;

    /** 各节点错误（节点名 -> 错误消息） */
    private Map<String, String> nodeErrors;

    /** 各节点重试次数 */
    private Map<String, Integer> nodeRetryCounts;

    /** 执行追踪日志 */
    private List<String> traces;

    /** 总耗时（毫秒） */
    private long totalCostMs;

    /** 总节点数 */
    private int totalNodes;

    /** 备注（如中止原因） */
    private String note;
}
