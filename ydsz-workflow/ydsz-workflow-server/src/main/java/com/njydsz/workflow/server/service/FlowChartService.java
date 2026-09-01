package com.njydsz.workflow.server.service;

import java.util.Set;

/**
 * 流程图渲染服务（P2-4）。
 *
 * <p>根据流程定义 XML 与当前实例状态，
 * 生成可视化的流程图（SVG/PNG），用于审批进度展示、通知嵌入、打印归档等场景。
 *
 * <p><b>节点状态着色规则：</b>
 *
 * <ul>
 *   <li><b>已完成节点（done）</b>：绿色填充 — 历史审批记录</li>
 *   <li><b>当前待办节点（active）</b>：蓝色填充 + 虚线边框 — 当前进行中</li>
 *   <li><b>未到达节点（pending）</b>：灰色填充 — 条件未触达或尚未执行</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * String svg = chartService.generateSvg(instanceId, activeNodes, doneNodes);
 * // 嵌入通知模板或前端 iframe
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.server.engine.BpmnDiagramParser BPMNDI 坐标解析
 */
public interface FlowChartService {

  /**
   * 生成 SVG 格式流程图。
   *
   * <p>基于 BPMNDI 解析出的节点/边坐标，生成内联 SVG。无 BPMNDI 时使用简单自上而下自动布局。
   *
   * @param definitionId   流程定义 ID
   * @param activeNodeCodes 当前待办节点编码集合（蓝色高亮）
   * @param doneNodeCodes   已完成节点编码集合（绿色）
   * @return SVG 字符串（可直接嵌入 HTML img/svg）
   */
  String generateSvg(String definitionId, Set<String> activeNodeCodes, Set<String> doneNodeCodes);

  /**
   * 生成 PNG 格式流程图字节数组。
   *
   * <p>基于 SVG 渲染为位图，适合用于消息通知、打印输出等场景。
   *
   * @param definitionId   流程定义 ID
   * @param activeNodeCodes 当前待办节点编码集合
   * @param doneNodeCodes   已完成节点编码集合
   * @return PNG 字节数组，渲染失败时返回 null
   */
  byte[] generatePng(String definitionId, Set<String> activeNodeCodes, Set<String> doneNodeCodes);
}
