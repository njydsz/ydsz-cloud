package com.njydsz.pmis.agent.orchestration.dag;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DAG 执行结果（P3-2 落地）。
 *
 * <p>一次 {@link DagDefinition} 执行的完整结果，包括整体状态、
 * 各节点状态、各节点输出、执行追踪、耗时等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Data
@Builder
public class DagExecutionResult implements Serializable {

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

    /** 执行追踪 */
    private List<DagExecutionTrace> traces;

    /** 总耗时（毫秒） */
    private long totalCostMs;

    /** 成功节点数 */
    private int successCount;

    /** 失败节点数 */
    private int failedCount;

    /** 跳过节点数 */
    private int skippedCount;

    /** 总节点数 */
    private int totalNodes;

    /** 备注（如中止原因） */
    private String note;

    /**
     * 获取指定节点的重试次数。
     *
     * @param nodeName 节点名
     * @return 重试次数，若未记录则返回 0
     */
    public int getRetryCount(String nodeName) {
        Integer count = nodeRetryCounts == null ? null : nodeRetryCounts.get(nodeName);
        return count == null ? 0 : count;
    }
}
