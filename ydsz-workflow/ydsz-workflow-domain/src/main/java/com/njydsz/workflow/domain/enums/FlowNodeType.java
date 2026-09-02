package com.njydsz.workflow.domain.enums;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.workflow.domain.exception.WorkflowExceptionCode;

/**
 * 流程节点类型
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum FlowNodeType {

  /** 开始节点 */
  START(0, "开始"),
  /** 审批节点（单人审批） */
  APPROVAL(1, "审批"),
  /** 抄送节点 */
  CC(2, "抄送"),
  /** 条件路由节点（互斥网关） */
  CONDITION(3, "条件"),
  /** 并行网关（同时推进多条分支） */
  PARALLEL(4, "并行网关"),
  /** 包容网关（满足条件的分支都推进） */
  INCLUSIVE(5, "包容网关"),
  /** 结束节点 */
  END(6, "结束"),
  /** 子流程节点 */
  SUBPROCESS(7, "子流程"),
  /**
   * P1-4: 服务节点 — 自动执行（HTTP/SCRIPT/AUTO_PASS），不创建人工任务。
   *
   * <p>ext JSON 配置：
   *
   * <ul>
   *   <li>serviceType: HTTP / SCRIPT / AUTO_PASS（默认 AUTO_PASS）
   *   <li>url: HTTP 调用地址（serviceType=HTTP 时必填）
   *   <li>method: HTTP 方法 GET/POST/PUT/DELETE（默认 GET）
   *   <li>script: 脚本内容（serviceType=SCRIPT 时使用，Aviator 语法，由 FlowServiceNodeExecutor 沙箱执行）
   * </ul>
   *
   * <p>P1-5: ext JSON 还支持 autoDedup: true 配置，表示该节点启用跨节点办理人去重 （同实例下已审批过的办理人将被排除，候选为空时自动跳过）。
   */
  SERVICE(8, "服务节点"),
  /**
   * GAP-P2-10: 循环节点（FOREACH）— 对集合变量中每个元素创建独立子任务，全部完成才推进
   *
   * <p>兼容 BPMN 2.0 multiInstance，支持"审批人动态集合"能力。 与 {@link #APPROVAL} +
   * performType=PARALLEL（会签）的区别：
   *
   * <ul>
   *   <li>会签：1 条 task + N 个 FlowUser（共享审批意见）
   *   <li>FOREACH：N 条独立 task（每条有自己的 assigneeId / iterVar）
   * </ul>
   *
   * <p>ext JSON 配置：
   *
   * <ul>
   *   <li>{@code collection}：集合变量名（如 {@code ${assignees}}，复用 expandAssignees 展开逻辑）
   *   <li>{@code elementVariable}：每次迭代注入的变量名（如 {@code assignee}，存入 task.iterVar）
   *   <li>{@code completionCondition}：完成条件表达式（注入 nrOfInstances / nrOfCompletedInstances /
   *       nrOfActiveInstances）
   *   <li>{@code emptyStrategy}：集合为空兜底策略（FALLBACK/AUTO_PASS/TRANSFER_ADMIN/ASSIGN_SPECIFIED）
   * </ul>
   */
  FOREACH(9, "循环节点"),
  /**
   * P0-4: 逐级审批节点 — 从发起人直属上级开始，逐级向上审批，直到达到 maxLevel 或遇到终止条件
   *
   * <p>逐级审批节点类型。与 {@link #APPROVAL} + performType=PARALLEL 的区别：
   *
   * <ul>
   *   <li>并行会签：办理人在流程定义时固定（permissionFlag 配置），全部通过才推进
   *   <li>逐级审批：办理人在运行时动态计算（从发起人上级开始逐级向上），无需预配置具体审批人
   * </ul>
   *
   * <p>ext JSON 配置：
   *
   * <ul>
   *   <li>{@code maxLevel}：最大审批级数（如 3 表示直属上级 → 上上级 → 上上上级）
   *   <li>{@code stopAtPosition}：遇到指定岗位时停止（如 "GM" 表示遇到总经理就停）
   *   <li>{@code stopAtUserId}：遇到指定用户时停止（如 "1001"）
   *   <li>{@code includeCurrentLevel}：是否包含发起人本级（默认 false）
   *   <li>{@code startFromInitiator}：是否从发起人开始（默认 false，从直属上级开始）
   * </ul>
   *
   * <p>实现：创建任务时通过 {@link FlowAssigneeResolver#expandMultiLeader} 展开多级上级列表，
   * 使用 PARALLEL 会签模式，全部通过后推进。
   */
  LEVEL_APPROVAL(10, "逐级审批"),
  /**
   * P0-5: AI 审批节点 — 由 AI Agent 根据流程变量智能决策是否通过，无需人工干预。
   *
   * <p>ext JSON 配置：
   *
   * <ul>
   *   <li>{@code agentId}：Agent ID（必填），由 ydsz-agent 模块创建并管理
   *   <li>{@code promptTemplate}：提示词模板（支持 {@code ${variable}} 占位符替换）
   *   <li>{@code outputSchema}：期望输出 JSON Schema（校验 Agent 输出结构）
   *   <li>{@code fallbackStrategy}：Agent 超时/异常时的兜底策略（AUTO_PASS / AUTO_REJECT /
   *       TRANSFER_ADMIN / RETRY，默认 AUTO_PASS）
   *   <li>{@code retryMax}：最大重试次数（默认 1）
   *   <li>{@code timeoutMs}：单次调用超时毫秒（默认 30000）
   * </ul>
   *
   * <p>execute 时：组装请求 → 调用 ydsz-agent 执行 Agent → 解析输出（approve/reject + reason）→ 根据结果自动推进流程。
   *
   * <p>实现了 Flowlong 的「AI 审批」概念，与 ydsz-agent 模块联动，支持自然语言审批决策。
   */
  AI_AGENT(11, "AI审批");

  private final int code;
  private final String desc;

  FlowNodeType(int code, String desc) {
    this.code = code;
    this.desc = desc;
  }

  public int getCode() {
    return code;
  }

  public String getDesc() {
    return desc;
  }

  /**
   * 根据节点编码解析节点类型。
   *
   * <p>入参为 {@code null} 或编码无匹配时统一回退为 {@link #APPROVAL}（单人审批）， 保证历史/脏数据可继续流转；调用方需严格校验时应使用
   * {@link #ofStrict(Integer)}。
   *
   * @param code 节点编码，可为 {@code null}
   * @return 匹配的节点类型；无匹配或入参为 {@code null} 时返回 {@link #APPROVAL}
   */
  public static FlowNodeType of(Integer code) {
    if (code == null) {
      return APPROVAL;
    }
    for (FlowNodeType t : values()) {
      if (t.code == code) {
        return t;
      }
    }
    return APPROVAL;
  }

  /**
   * 严格解析节点类型：无匹配或入参为 {@code null} 时抛出异常（fail-fast）。
   *
   * <p>适用于流程定义解析/部署等需要"非法数据尽早暴露"的路径（如 BPMN 解析、定义反序列化），
   * 避免未知节点类型被静默降级为 {@link #APPROVAL} 造成线上语义漂移。
   *
   * @param code 节点编码，不可为 {@code null}
   * @return 匹配的节点类型
   * @throws BusinessException 当入参为 {@code null} 或无匹配时，异常码为
   *     {@link WorkflowExceptionCode#UNSUPPORTED_BPMN_ELEMENT}
   */
  public static FlowNodeType ofStrict(Integer code) {
    if (code == null) {
      throw BusinessException.builder()
          .resultCode(WorkflowExceptionCode.UNSUPPORTED_BPMN_ELEMENT)
          .params("null")
          .build();
    }
    for (FlowNodeType t : values()) {
      if (t.code == code) {
        return t;
      }
    }
    throw BusinessException.builder()
        .resultCode(WorkflowExceptionCode.UNSUPPORTED_BPMN_ELEMENT)
        .params(code)
        .build();
  }
}
