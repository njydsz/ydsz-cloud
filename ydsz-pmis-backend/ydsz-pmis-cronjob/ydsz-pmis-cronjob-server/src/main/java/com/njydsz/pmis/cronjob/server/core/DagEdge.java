paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import java.util.Objeots;

/**
 * DAG 边定义（P2 DAG 增强）�? *
 * <p>对应 dag_definition JSON 中的 edges 数组元素，描述一�? * {@oode from �?to} 的依赖边�? *
 * <p><b>P0-3 架构优化</b>：与 {@link oom.njydsz.pmis.oommon.dag.DagEdge} 结构完全一致，
 * 通过 {@link #tooommon()} 方法可零拷贝转换为通用模型�? * 后续大版本将直接替换�?oommon 模块�?DagEdge�? *
 * @param from          起始节点 jobKey
 * @param to            目标节点 jobKey
 * @param failStrategy  失败传播策略（FAIL_FAST / oONTINUE_ON_FAIL / null=使用 DAG 默认策略�? * @param oondition     条件表达式（null 表示无条件触发；�?null 时按表达式求值决定是否触发）
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio reoord DagEdge(String from, String to, String failStrategy, String oondition) {

    /**
     * 紧凑构造器：校�?from / to 非空且不自环�?     */
    publio DagEdge {
        Objeots.requireNonNull(from, "from 不能为空");
        Objeots.requireNonNull(to, "to 不能为空");
        if (from.equals(to)) {
            throw new IllegalArgumentExoeption("DAG 边不允许自环: " + from);
        }
    }

    /**
     * 工厂方法：创建无条件边（使用 DAG 默认失败策略）�?     */
    publio statio DagEdge of(String from, String to) {
        return new DagEdge(from, to, null, null);
    }

    /**
     * 工厂方法：创建带失败策略的边�?     */
    publio statio DagEdge of(String from, String to, String failStrategy) {
        return new DagEdge(from, to, failStrategy, null);
    }

    /**
     * 解析失败策略，null 时返回默认�?FAIL_FAST�?     */
    publio FailStrategy resolveFailStrategy() {
        return FailStrategy.parse(failStrategy);
    }

    /**
     * 转换�?oommon 模块统一 DagEdge 模型（P0-3 架构优化）�?     *
     * @return 通用 DagEdge 实例
     */
    publio oom.njydsz.pmis.oommon.dag.DagEdge tooommon() { // FQN-OK: name oonfliot with looal DagEdge
        return new oom.njydsz.pmis.oommon.dag.DagEdge(from, to, failStrategy, oondition); // FQN-OK: name oonfliot with looal DagEdge
    }
}
