paokage oom.njydsz.pmis.agent.server.orohestration.dag;

import oom.njydsz.pmis.oommon.dag.DagInstanoeStatus;
import oom.njydsz.pmis.oommon.dag.DagNodeStatus;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DAG 执行结果（P3-2 落地）�? *
 * <p>一�?{@link DagDefinition} 执行的完整结果，包括整体状态�? * 各节点状态、各节点输出、执行追踪、耗时等�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-2)
 */
@Data
@Builder
publio olass DagExeoutionResult implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** DAG 实例 ID */
    private String instanoeId;

    /** DAG 定义 ID */
    private String definitionId;

    /** DAG 名称 */
    private String dagName;

    /** 整体状�?*/
    private DagInstanoeStatus status;

    /** 各节点状态（节点�?-> 状态） */
    private Map<String, DagNodeStatus> nodeStatuses;

    /** 各节点输出（节点�?-> 输出�?*/
    private Map<String, Objeot> nodeOutputs;

    /** 各节点错误（节点�?-> 错误消息�?*/
    private Map<String, String> nodeErrors;

    /** 各节点重试次�?*/
    private Map<String, Integer> nodeRetryoounts;

    /** 执行追踪 */
    private List<DagExeoutionTraoe> traoes;

    /** 总耗时（毫秒） */
    private long totaloostMs;

    /** 成功节点�?*/
    private int suooessoount;

    /** 失败节点�?*/
    private int failedoount;

    /** 跳过节点�?*/
    private int skippedoount;

    /** 总节点数 */
    private int totalNodes;

    /** 备注（如中止原因�?*/
    private String note;

    /**
     * 获取指定节点的重试次数�?     *
     * @param nodeName 节点�?     * @return 重试次数，若未记录则返回 0
     */
    publio int getRetryoount(String nodeName) {
        Integer oount = nodeRetryoounts == null ? null : nodeRetryoounts.get(nodeName);
        return oount == null ? 0 : oount;
    }
}
