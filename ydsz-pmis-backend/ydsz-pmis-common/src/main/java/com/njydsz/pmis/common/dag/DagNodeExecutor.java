package com.njydsz.pmis.common.dag;

import java.util.Map;

/**
 * DAG 节点执行器 SPI（P1-1 架构优化）。
 *
 * <p>各业务模块（agent / cronjob）实现此接口，提供具体的节点执行逻辑。
 * 统一 DAG 引擎通过此接口调用节点，不关心具体实现是 Agent 调用还是 Job 执行。
 *
 * <h3>实现示例</h3>
 * <ul>
 *   <li><b>agent 模块</b>：根据 nodeType 查找 {@code Agent} 实例并调用 {@code agent.execute()}</li>
 *   <li><b>cronjob 模块</b>：根据 nodeType 查找 {@code JobHandler} 并触发任务执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface DagNodeExecutor {

    /**
     * 执行单个 DAG 节点。
     *
     * <p>由统一 DAG 引擎在调度到该节点时调用。实现方应完成以下步骤：
     * <ol>
     *   <li>根据 {@code nodeType} 查找具体的执行器（Agent / JobHandler）</li>
     *   <li>合并 {@code nodeInputs} 和 {@code sharedVariables} 作为输入</li>
     *   <li>执行业务逻辑并返回输出</li>
     * </ol>
     *
     * @param nodeId          节点 ID（DAG 内唯一）
     * @param nodeType        节点类型（如 Agent 的 agentType / cronjob 的 jobKey）
     * @param nodeInputs      节点级输入参数（可空）
     * @param sharedVariables DAG 共享变量（上游节点输出已注入）
     * @return 节点执行结果（将注入共享变量供下游使用）
     * @throws Exception 执行失败时抛出，由引擎按失败策略处理
     */
    Object execute(String nodeId, String nodeType,
                   Map<String, Object> nodeInputs,
                   Map<String, Object> sharedVariables) throws Exception;

    /**
     * 检查是否支持指定节点类型。
     *
     * @param nodeType 节点类型
     * @return true 表示本执行器可以处理该类型
     */
    boolean supports(String nodeType);
}
