package com.njydsz.workflow.server.service;

import java.util.List;
import java.util.Map;

/**
 * 流程路由服务。
 * <p>根据条件选择下一节点/分支。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


public interface FlowRoutingService {

    /**
     * 评估路由条件表达式，返回匹配的节点编码
     *
     * <p>表达式示例：
     * <pre>{@code
     *   amount > 100000 ? 'node_high_amount' : 'node_low_amount'
     *   budget_type == 'CAPEX' ? 'capex_approval' : 'opex_approval'
     * }</pre>
     *
     * @param conditionExpression 路由条件表达式（Aviator 语法）
     * @param variables           流程变量上下文
     * @return 匹配的节点编码；评估失败或无匹配返回 null
     */
    String evaluateRoute(String conditionExpression, Map<String, Object> variables);

    /**
     * 评估布尔条件表达式
     *
     * <p>用于判断跳转边是否满足条件，支持 Aviator 语法。
     *
     * @param conditionExpression 条件表达式（Aviator 语法，如 amount > 100000）
     * @param variables           流程变量上下文
     * @return true=条件成立，false=不成立
     */
    boolean evaluateCondition(String conditionExpression, Map<String, Object> variables);

    /**
     * 检测流程异常
     *
     * <p>覆盖三种异常场景：
     * <ul>
     *   <li><b>超时检测</b>：任务超过 dueAt 截止时间仍未完成</li>
     *   <li><b>卡单检测</b>：任务在同一节点停留超过 24 小时</li>
     *   <li><b>循环审批</b>：审计日志中同一节点被反复驳回超过 3 次</li>
     * </ul>
     *
     * <p>每条异常记录的 Map 结构：
     * <pre>{@code
     *   {
     *     "type": "TIMEOUT|STUCK|LOOP",
     *     "nodeCode": "...",
     *     "nodeName": "...",
     *     "description": "...",
     *     ...  // 各类型特有字段
     *   }
     * }</pre>
     *
     * @param instanceId 流程实例 ID
     * @return 异常记录列表；无异常返回空列表
     */
    List<Map<String, Object>> detectAnomalies(String instanceId);

    /**
     * 判断流程实例是否异常
     *
     * <p>等价于 {@code !detectAnomalies(instanceId).isEmpty()}。
     *
     * @param instanceId 流程实例 ID
     * @return true=存在异常，false=正常
     */
    boolean isAnomaly(String instanceId);
}