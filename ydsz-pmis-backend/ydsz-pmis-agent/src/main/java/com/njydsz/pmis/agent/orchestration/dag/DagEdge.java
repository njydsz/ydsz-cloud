package com.njydsz.pmis.agent.orchestration.dag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * DAG 条件边（P1-4 落地）。
 *
 * <p>对标 Coze Router / Dify Conditional Branch / LangGraph Conditional Edges：
 * 支持在节点间定义带条件的边，实现动态路由。
 *
 * <p>与 {@link DagNode#getDependsOn()} 的区别：
 * <ul>
 *   <li>{@code dependsOn} 是无条件依赖——前置节点成功后必定执行当前节点</li>
 *   <li>{@code DagEdge} 是条件依赖——前置节点成功后，还需条件表达式求值为 true 才执行目标节点</li>
 * </ul>
 *
 * <p>典型场景：
 * <pre>
 *   风险评估节点 → [score > 0.8] → 高风险处理节点
 *               → [score <= 0.8 && score > 0.5] → 中风险处理节点
 *               → [else] → 低风险处理节点
 * </pre>
 *
 * <p>条件表达式使用 SpEL 语法，求值上下文为上游节点的输出（通过 {@link DagExecutionContext#getSharedVariables()}）。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P1-4)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DagEdge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 边的起始节点名称（源节点）。
     */
    private String from;

    /**
     * 边的目标节点名称（目标节点）。
     */
    private String to;

    /**
     * 条件表达式（SpEL 语法，可选）。
     *
     * <p>为 null 或空时表示无条件边（等同于 dependsOn）。
     * 表达式上下文为上游节点输出 + 共享变量。
     *
     * <p>示例：
     * <ul>
     *   <li>{@code #score > 0.8} - 上游输出中 score > 0.8</li>
     *   <li>{@code #result.status == 'HIGH'} - 上游输出的 result.status 为 HIGH</li>
     *   <li>{@code #amount > 10000 && #level == 'A'} - 复合条件</li>
     * </ul>
     */
    private String condition;

    /**
     * 边的标签/名称（可选，用于 UI 展示和日志）。
     */
    private String label;

    /**
     * 优先级（当多个条件边 from 同一节点时，按优先级降序匹配，第一个满足的生效）。
     *
     * <p>默认为 0，数字越大优先级越高。
     */
    private int priority;

    /**
     * 是否为默认边（当所有条件边都不满足时走的路径，类似于 switch-default）。
     *
     * <p>一个节点的出边中最多只能有一个 default 边。
     */
    private boolean defaultEdge;
}
