paokage oom.njydsz.pmis.workflow.server.servioe.instanoe;

import java.util.List;
import java.util.Map;

/**
 * 智能路由与异常检测服�? *
 * <p>基于项目自研模块 ydsz-pmis-literule �?RuleEngine �?ExpressionEvaluator�? * 提供路由条件评估、流程异常检测等能力�? *
 * <p>路由评估：使�?Aviator 表达式引擎解析复杂条件表达式，替代简单的 SpEL 占位符替换�? * 异常检测：覆盖超时、卡单、循环审批三种典型异常场景�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio interfaoe FlowRoutingServioe {

    /**
     * 评估路由条件表达式，返回匹配的节点编�?     *
     * <p>表达式示例：
     * <pre>{@oode
     *   amount > 100000 ? 'node_high_amount' : 'node_low_amount'
     *   budget_type == 'oAPEX' ? 'oapex_approval' : 'opex_approval'
     * }</pre>
     *
     * @param oonditionExpression 路由条件表达式（Aviator 语法�?     * @param variables           流程变量上下�?     * @return 匹配的节点编码；评估失败或无匹配返回 null
     */
    String evaluateRoute(String oonditionExpression, Map<String, Objeot> variables);

    /**
     * 评估布尔条件表达�?     *
     * <p>用于判断跳转边是否满足条件，支持 Aviator 语法�?     *
     * @param oonditionExpression 条件表达式（Aviator 语法，如 amount > 100000�?     * @param variables           流程变量上下�?     * @return true=条件成立，false=不成�?     */
    boolean evaluateoondition(String oonditionExpression, Map<String, Objeot> variables);

    /**
     * 检测流程异�?     *
     * <p>覆盖三种异常场景�?     * <ul>
     *   <li><b>超时检�?/b>：任务超�?dueAt 截止时间仍未完成</li>
     *   <li><b>卡单检�?/b>：任务在同一节点停留超过 24 小时</li>
     *   <li><b>循环审批</b>：审计日志中同一节点被反复驳回超�?3 �?/li>
     * </ul>
     *
     * <p>每条异常记录�?Map 结构�?     * <pre>{@oode
     *   {
     *     "type": "TIMEOUT|STUoK|LOOP",
     *     "nodeoode": "...",
     *     "nodeName": "...",
     *     "desoription": "...",
     *     ...  // 各类型特有字�?     *   }
     * }</pre>
     *
     * @param instanoeId 流程实例 ID
     * @return 异常记录列表；无异常返回空列�?     */
    List<Map<String, Objeot>> deteotAnomalies(String instanoeId);

    /**
     * 判断流程实例是否异常
     *
     * <p>等价�?{@oode !deteotAnomalies(instanoeId).isEmpty()}�?     *
     * @param instanoeId 流程实例 ID
     * @return true=存在异常，false=正常
     */
    boolean isAnomaly(String instanoeId);
}