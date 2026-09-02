package com.njydsz.cronjob.server.core.dag;

import java.util.Objects;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonProperty;

/**
 * DAG 边定义（P2 DAG 增强）。
 *
 * <p>对应 dag_definition JSON 中的 edges 数组元素，描述一条 {@code from → to} 的依赖边。
 *
 * <p><b>P0-3 架构优化</b>：与 {@link com.njydsz.cronjob.domain.dag.DagEdge} 结构完全一致， 通过 {@link
 * #toCommon()} 方法可零拷贝转换为通用模型。 后续大版本将直接替换为 common 模块的 DagEdge。
 *
 * @param from 起始节点 jobKey
 * @param to 目标节点 jobKey
 * @param failStrategy 失败传播策略（FAIL_FAST / CONTINUE_ON_FAIL / null=使用 DAG 默认策略）
 * @param condition 条件表达式（null 表示无条件触发；非 null 时按表达式求值决定是否触发）
 * @author ydsz-team
 * @since 26.09.01
 */
@JsonClass(description = "DAG 边定义，标记可安全反序列化")
public record DagEdge(
    @JsonProperty("from") String from, String to, String failStrategy, String condition) {

  /** 紧凑构造器：校验 from / to 非空且不自环。 */
  public DagEdge {
    Objects.requireNonNull(from, "from 不能为空");
    Objects.requireNonNull(to, "to 不能为空");
    if (from.equals(to)) {
      throw new IllegalArgumentException("DAG 边不允许自环: " + from);
    }
  }

  /** 工厂方法：创建无条件边（使用 DAG 默认失败策略）。 */
  public static DagEdge of(String from, String to) {
    return new DagEdge(from, to, null, null);
  }

  /** 工厂方法：创建带失败策略的边。 */
  public static DagEdge of(String from, String to, String failStrategy) {
    return new DagEdge(from, to, failStrategy, null);
  }

  /** 解析失败策略，null 时返回默认值 ABORT。 */
  public DagFailureStrategy resolveFailStrategy() {
    return DagFailureStrategy.parse(failStrategy);
  }
}
