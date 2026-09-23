package com.njydsz.workflow.domain.engine;

import java.util.List;
import java.util.Map;

import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;

/**
 * 流程推进器策略接口（Domain 层契约）。
 *
 * <p>定义流程引擎核心的「路由计算 + 推进」能力抽象，隔离具体的引擎实现细节。
 * 领域层通过此接口查询流程的下一批目标节点、执行会签聚合判断，<b>不依赖</b>任何具体引擎实现类。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li><b>策略模式</b>：不同场景可注入不同推进器实现，引擎核心逻辑与扩展场景解耦
 *   <li><b>不可变无状态</b>：推进器本身无状态，所有状态通过参数传入，线程安全
 *   <li><b>纯路由语义</b>：接口方法仅做路由计算，<b>不</b>直接创建任务或修改实例状态
 * </ul>
 *
 * <p><b>典型实现：</b>
 *
 * <ul>
 *   <li>{@code DefaultFlowAdvancer} — 默认推进器：全路由、条件评估、join 聚合（server 层实现）
 *   <li>{@code BatchFlowAdvancer} — 批量审批快速路径（二阶段拓展）
 *   <li>{@code SimplifiedFlowAdvancer} — 极简审批场景（跳过路由直接推进到结束节点）
 * </ul>
 *
 * <p><b>架构合规说明（YDIZ-ARCH-001 / YDIZ-DDD-005）：</b>引擎接口位于 {@code domain/engine/}，
 * 处于领域层；server 层 {@code DefaultFlowAdvancer} 通过 {@code @Component} 注入实现。
 * server 层可依赖 domain 接口，domain 层禁止反向依赖 server 层。
 *
 * @author ydsz-team
 * @since 26.09.23
 * @see com.njydsz.workflow.domain.vo.FlowInstanceVO
 * @see com.njydsz.workflow.domain.vo.FlowNodeVO
 */
public interface FlowAdvancer {

  /**
   * 计算流程从当前节点正向推进后应到达的下一批节点（纯路由计算，无副作用）。
   *
   * <p><b>本方法是纯粹的「路由计算」</b>：只返回目标节点列表，<b>不</b>创建任务、<b>不</b>修改实例状态。
   * 任务生成与状态流转由调用方（{@code FlowInstanceService}）在同一事务中统一提交。
   *
   * <p><b>路由规则：</b>
   *
   * <ul>
   *   <li>出边按网关语义筛选：排他网关只取首条匹配，包容网关取全部匹配
   *   <li>均无匹配时走 BPMN {@code default} 出边；连 default 也为空则返回空列表（流程结束）
   *   <li>目标节点不存在时跳过并告警，不中断其余分支
   * </ul>
   *
   * @param instance 当前流程实例（ {@code definitionId} 有效），不可为 {@code null}
   * @param currentNodeCode 当前节点编码，不可为 {@code null}
   * @param variables 流程变量（用于条件表达式求值），可为 {@code null}
   * @return 下一批目标节点列表；空列表表示流程无下游
   * @throws com.njydsz.common.exception.custom.SysException 节点不存在时
   */
  List<FlowNodeVO> resolveNextNodes(FlowInstanceVO instance, String currentNodeCode,
      Map<String, Object> variables);

  /**
   * 计算 REJECT 回退时的目标节点编码。
   *
   * <p>回退策略（优先级由高到低）：
   *
   * <ol>
   *   <li>显式传入 {@code targetNodeCode} 时优先按其回退（支持跳退到任意历史节点）
   *   <li>未指定时取当前节点第一条入边的 sourceRef
   *   <li>无前驱时回退到开始节点
   * </ol>
   *
   * @param definitionId 流程定义 ID，不可为 {@code null}
   * @param currentNodeCode 当前节点编码，不可为 {@code null}
   * @param targetNodeCode 回退目标节点编码（可空，为空时自动推导）
   * @return 回退目标节点编码
   * @throws com.njydsz.common.exception.custom.SysException 节点不存在或无法推导回退目标时
   */
  String resolveRejectTarget(String definitionId, String currentNodeCode, String targetNodeCode);

  /**
   * 评估跳转条件表达式。
   *
   * <p>评估优先级：引擎内置路由服务 → 变量策略（Aviator / SpEL）。
   *
   * @param condition 跳转条件表达式（空/空串 视为无条件成立）
   * @param variables 流程变量，可为 {@code null}
   * @return {@code true} = 条件成立；{@code false} = 不成立
   */
  boolean evaluateCondition(String condition, Map<String, Object> variables);

  /**
   * 判断 join 节点是否已满足聚合条件。
   *
   * <p>用于并行/包容网关 join 聚合：令牌服务（Redis）精确跟踪分支到达，
   * 异常时降级为扫描活跃任务数。
   *
   * <p><b>本方法可能产生副作用</b>：初始化 join 令牌、标记分支到达。
   *
   * @param instance 当前流程实例
   * @param joinNode join 类型目标节点
   * @return {@code true} = 满足聚合条件可继续；{@code false} = 仍需等待其他分支
   * @throws com.njydsz.common.exception.custom.SysException 节点非 join 类型时
   */
  boolean tryAggregateJoin(FlowInstanceVO instance, FlowNodeVO joinNode);
}
