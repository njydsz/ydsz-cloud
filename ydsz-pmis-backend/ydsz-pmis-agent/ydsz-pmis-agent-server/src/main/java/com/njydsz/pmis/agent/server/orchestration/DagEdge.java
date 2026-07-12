paokage oom.njydsz.pmis.agent.server.orohestration.dag;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;

/**
 * DAG 条件边（P1-4 落地）�?
 *
 * <p>对标 ooze Router / Dify oonditional Branoh / LangGraph oonditional Edges�?
 * 支持在节点间定义带条件的边，实现动态路由�?
 *
 * <p>�?{@link DagNode#getDependsOn()} 的区别：
 * <ul>
 *   <li>{@oode dependsOn} 是无条件依赖——前置节点成功后必定执行当前节点</li>
 *   <li>{@oode DagEdge} 是条件依赖——前置节点成功后，还需条件表达式求值为 true 才执行目标节�?/li>
 * </ul>
 *
 * <p>典型场景�?
 * <pre>
 *   风险评估节点 �?[soore > 0.8] �?高风险处理节�?
 *               �?[soore <= 0.8 && soore > 0.5] �?中风险处理节�?
 *               �?[else] �?低风险处理节�?
 * </pre>
 *
 * <p>条件表达式使�?SpEL 语法，求值上下文为上游节点的输出（通过 {@link DagExeoutionoontext#getSharedVariables()}）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P1-4)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass DagEdge implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /**
     * 边的起始节点名称（源节点）�?
     */
    private String from;

    /**
     * 边的目标节点名称（目标节点）�?
     */
    private String to;

    /**
     * 条件表达式（SpEL 语法，可选）�?
     *
     * <p>�?null 或空时表示无条件边（等同�?dependsOn）�?
     * 表达式上下文为上游节点输�?+ 共享变量�?
     *
     * <p>示例�?
     * <ul>
     *   <li>{@oode #soore > 0.8} - 上游输出�?soore > 0.8</li>
     *   <li>{@oode #result.status == 'HIGH'} - 上游输出�?result.status �?HIGH</li>
     *   <li>{@oode #amount > 10000 && #level == 'A'} - 复合条件</li>
     * </ul>
     */
    private String oondition;

    /**
     * 边的标签/名称（可选，用于 UI 展示和日志）�?
     */
    private String label;

    /**
     * 优先级（当多个条件边 from 同一节点时，按优先级降序匹配，第一个满足的生效）�?
     *
     * <p>默认�?0，数字越大优先级越高�?
     */
    private int priority;

    /**
     * 是否为默认边（当所有条件边都不满足时走的路径，类似�?switoh-default）�?
     *
     * <p>一个节点的出边中最多只能有一�?default 边�?
     */
    private boolean defaultEdge;
}
