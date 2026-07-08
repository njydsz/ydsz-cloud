package com.njydsz.pmis.agent.orchestration.dag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * 状态机编排器（P4-10 落地）。
 *
 * <p>对标 LangGraph StateGraph / Coze 状态机编排：
 * <ul>
 *   <li>基于状态转换（State → Transition → State）驱动流程执行</li>
 *   <li>支持条件分支（根据 Agent 输出决定下一个状态）</li>
 *   <li>支持循环（LLM 判断是否需要重新执行某步骤）</li>
 *   <li>支持子图嵌套（一个状态可引用另一个完整 DAG）</li>
 * </ul>
 *
 * <p>与 {@link DagExecutor} 的区别：
 * <ul>
 *   <li>DAG 是无环的，适合固定流程</li>
 *   <li>状态机支持循环和条件回退，适合需要 LLM 动态决策的场景</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * StateMachineOrchestrator sm = StateMachineOrchestrator.builder()
 *     .name("风险处理状态机")
 *     .initialState("collect")
 *     .state("collect", State.builder()
 *         .agentType("RISK_COLLECT")
 *         .transitions(List.of(
 *             Transition.builder().target("analyze").condition("success").build(),
 *             Transition.builder().target("collect").condition("retry").build()
 *         ))
 *         .build())
 *     .state("analyze", State.builder()
 *         .agentType("RISK_ANALYZE")
 *         .transitions(List.of(
 *             Transition.builder().target("report").condition("score>0.7").build(),
 *             Transition.builder().target("collect").condition("score<=0.7").build()
 *         ))
 *         .build())
 *     .state("report", State.builder()
 *         .agentType("RISK_REPORT")
 *         .terminal(true)
 *         .build())
 *     .build();
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-10)
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateMachineOrchestrator implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态机名称 */
    private String name;

    /** 初始状态名 */
    private String initialState;

    /** 状态定义（状态名 → 状态定义） */
    @Builder.Default
    private Map<String, State> states = new LinkedHashMap<>();

    /** 最大循环次数（防止无限循环） */
    @Builder.Default
    private int maxIterations = 20;

    /** 嵌套子图定义（子图名 → DagDefinition） */
    @Builder.Default
    private Map<String, DagDefinition> subGraphs = new LinkedHashMap<>();

    /**
     * 单个状态定义。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class State implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 关联的 Agent 类型 */
        private String agentType;

        /** 关联的子图名（引用 subGraphs 中的定义） */
        private String subGraphName;

        /** 状态转换规则 */
        private List<Transition> transitions;

        /** 是否终态 */
        private boolean terminal;

        /** 状态级输入参数 */
        private Map<String, Object> inputs;

        /** 超时时间（毫秒） */
        private long timeoutMs;
    }

    /**
     * 状态转换规则。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transition implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 目标状态名 */
        private String target;

        /**
         * 转换条件（简化表达式）。
         *
         * <p>支持的条件格式：
         * <ul>
         *   <li>{@code success} - Agent 执行成功</li>
         *   <li>{@code failure} - Agent 执行失败</li>
         *   <li>{@code score>0.7} - Agent 返回的 score > 0.7</li>
         *   <li>{@code score<=0.3} - Agent 返回的 score <= 0.3</li>
         *   <li>{@code always} - 无条件转换（默认）</li>
         * </ul>
         */
        private String condition;

        /** 优先级（多个条件同时满足时，取优先级高的） */
        @Builder.Default
        private int priority = 0;
    }

    /**
     * 执行状态机。
     *
     * @param currentState 当前状态名
     * @param agentResult  当前状态 Agent 的执行结果
     * @return 下一个状态名（null 表示终态）
     */
    public String nextState(String currentState, com.njydsz.pmis.agent.engine.AgentResult agentResult) {
        State state = states.get(currentState);
        if (state == null) {
            log.warn("[StateMachine] 状态 [{}] 不存在", currentState);
            return null;
        }
        if (state.isTerminal()) {
            return null; // 终态
        }
        if (state.getTransitions() == null || state.getTransitions().isEmpty()) {
            return null; // 无转换规则
        }

        // 按优先级排序，找到第一个满足条件的转换
        return state.getTransitions().stream()
                .sorted(Comparator.comparingInt(Transition::getPriority).reversed())
                .filter(t -> evaluateCondition(t.getCondition(), agentResult))
                .map(Transition::getTarget)
                .findFirst()
                .orElse(null);
    }

    /**
     * 评估转换条件。
     */
    private boolean evaluateCondition(String condition, com.njydsz.pmis.agent.engine.AgentResult result) {
        if (condition == null || condition.isBlank() || "always".equals(condition)) {
            return true;
        }

        boolean success = result != null && result.isSuccess();
        double score = result != null && result.getScore() != null ? result.getScore().doubleValue() : 0.0;

        if ("success".equals(condition)) return success;
        if ("failure".equals(condition)) return !success;

        // 简化数值比较：score>X, score>=X, score<X, score<=X
        if (condition.startsWith("score")) {
            String op = extractOperator(condition);
            double threshold = extractNumber(condition);
            return switch (op) {
                case ">" -> score > threshold;
                case ">=" -> score >= threshold;
                case "<" -> score < threshold;
                case "<=" -> score <= threshold;
                case "==", "=" -> Math.abs(score - threshold) < 0.001;
                default -> false;
            };
        }

        return false;
    }

    /** 提取比较运算符 */
    private static String extractOperator(String expr) {
        for (String op : new String[]{">=", "<=", "==", ">", "<", "="}) {
            if (expr.contains(op)) return op;
        }
        return ">";
    }

    /** 提取数值 */
    private static double extractNumber(String expr) {
        String numStr = expr.replaceAll("[^0-9.\\-]", "");
        try {
            return Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
