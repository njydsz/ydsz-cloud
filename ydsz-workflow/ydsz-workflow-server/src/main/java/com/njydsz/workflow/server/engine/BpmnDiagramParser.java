package com.njydsz.workflow.server.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * BPMNDI 坐标解析器
 *
 * <p>负责解析 BPMN 2.0 XML 中的 BPMNDI（Diagram Interchange）段，提取节点和边的可视化坐标。
 * 用于驱动流程图回放与 SVG 可视化高亮。
 *
 * <p>BPMN XML 顶层结构（节选）：
 *
 * <pre>
 * &lt;definitions ...&gt;
 *   &lt;process id="..."&gt;...&lt;/process&gt;
 *   &lt;BPMNDiagram id="..."&gt;
 *     &lt;BPMNPlane bpmnElement="..."&gt;
 *       &lt;BPMNShape id="..." bpmnElement="node1"&gt;
 *         &lt;Bounds x="100" y="80" width="100" height="60"/&gt;
 *       &lt;/BPMNShape&gt;
 *       &lt;BPMNEdge id="..." bpmnElement="flow1"&gt;
 *         &lt;waypoint x="200" y="110"/&gt;
 *         &lt;waypoint x="300" y="110"/&gt;
 *       &lt;/BPMNEdge&gt;
 *     &lt;/BPMNPlane&gt;
 *   &lt;/BPMNDiagram&gt;
 * &lt;/definitions&gt;
 * </pre>
 *
 * <p>无 BPMNDI 段时（手写或简化 BPMN）跳过，map 保持为空，调用方需降级到自动布局。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BpmnDiagramParser {


  private final BpmnElementHelper bpmnElementHelper;

  /**
   * 解析 BPMN 2.0 BPMNDI 段（Diagram Interchange），提取节点和边的可视化坐标。
   *
   * <p>解析过程：
   *
   * <ol>
   *   <li>遍历根 &lt;definitions&gt; 找 &lt;BPMNDiagram&gt; / &lt;BPMNPlane&gt;
   *   <li>遍历 &lt;BPMNShape&gt; 读取 Bounds（x/y/width/height），key = bpmnElement（节点 id）
   *   <li>遍历 &lt;BPMNEdge&gt; 读取所有 waypoint（折线拐点），key = bpmnElement（边 id）
   * </ol>
   *
   * @param root &lt;definitions&gt; 根节点
   * @param nodeCoords 输出：节点坐标映射（key = nodeCode）
   * @param skipCoords 输出：边坐标映射（key = sequenceFlowId）
   */
  public void parseBpmnDiagram(
      Element root,
      Map<String, BpmnModel.NodeCoordinate> nodeCoords,
      Map<String, List<BpmnModel.NodeCoordinate>> skipCoords) {
    if (root == null) {
      return;
    }
    // 找 <BPMNDiagram>
    Element bpmnDiagram = bpmnElementHelper.findChildByLocalName(root, "BPMNDiagram");
    if (bpmnDiagram == null) {
      bpmnDiagram = bpmnElementHelper.findChildByLocalName(root, "bpmndiagram");
    }
    if (bpmnDiagram == null) {
      log.debug("[BpmnDiagramParser] BPMN XML 未包含 <BPMNDiagram> 段，跳过坐标解析");
      return;
    }
    // 找 <BPMNPlane>
    Element bpmnPlane = bpmnElementHelper.findChildByLocalName(bpmnDiagram, "BPMNPlane");
    if (bpmnPlane == null) {
      bpmnPlane = bpmnElementHelper.findChildByLocalName(bpmnDiagram, "bpmnplane");
    }
    if (bpmnPlane == null) {
      return;
    }
    // 遍历 BPMNShape（节点）和 BPMNEdge（边）
    NodeList children = bpmnPlane.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (!(n instanceof Element child)) {
        continue;
      }
      String local = child.getLocalName();
      if (local == null) {
        local = child.getNodeName();
      }
      if ("BPMNShape".equalsIgnoreCase(local) || "bpmnshape".equals(local)) {
        parseBpmnShape(child, nodeCoords);
      } else if ("BPMNEdge".equalsIgnoreCase(local) || "bpmnedge".equals(local)) {
        parseBpmnEdge(child, skipCoords);
      }
    }
  }

  /**
   * 解析 BPMNShape：提取 Bounds，key = bpmnElement（节点 id）
   *
   * @param shape BPMNShape 元素
   * @param nodeCoords 输出：节点坐标映射
   */
  private void parseBpmnShape(
      Element shape, Map<String, BpmnModel.NodeCoordinate> nodeCoords) {
    String bpmnElement = shape.getAttribute("bpmnElement");
    if (bpmnElement == null || bpmnElement.isBlank()) {
      return;
    }
    // 找 <Bounds x y width height>
    Element bounds = bpmnElementHelper.findChildByLocalName(shape, "Bounds");
    if (bounds == null) {
      return;
    }
    try {
      double x = bpmnElementHelper.parseDouble(bounds.getAttribute("x"));
      double y = bpmnElementHelper.parseDouble(bounds.getAttribute("y"));
      double w = bpmnElementHelper.parseDouble(bounds.getAttribute("width"));
      double h = bpmnElementHelper.parseDouble(bounds.getAttribute("height"));
      nodeCoords.put(bpmnElement, new BpmnModel.NodeCoordinate(x, y, w, h));
    } catch (NumberFormatException nfe) {
      log.warn(
          "[BpmnDiagramParser] BPMNShape Bounds 解析失败: bpmnElement={}", bpmnElement);
    }
  }

  /**
   * 解析 BPMNEdge：提取所有 waypoint，key = bpmnElement（边 id）
   *
   * @param edge BPMNEdge 元素
   * @param skipCoords 输出：边坐标映射
   */
  private void parseBpmnEdge(
      Element edge, Map<String, List<BpmnModel.NodeCoordinate>> skipCoords) {
    String bpmnElement = edge.getAttribute("bpmnElement");
    if (bpmnElement == null || bpmnElement.isBlank()) {
      return;
    }
    NodeList children = edge.getChildNodes();
    List<BpmnModel.NodeCoordinate> waypoints = new ArrayList<>(children.getLength());
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (!(n instanceof Element wp)) {
        continue;
      }
      String local = wp.getLocalName();
      if (local == null) {
        local = wp.getNodeName();
      }
      if ("waypoint".equalsIgnoreCase(local) || "di:waypoint".equalsIgnoreCase(local)) {
        try {
          double x = bpmnElementHelper.parseDouble(wp.getAttribute("x"));
          double y = bpmnElementHelper.parseDouble(wp.getAttribute("y"));
          waypoints.add(new BpmnModel.NodeCoordinate(x, y));
        } catch (NumberFormatException nfe) {
          log.warn(
              "[BpmnDiagramParser] waypoint 解析失败: bpmnElement={}", bpmnElement);
        }
      }
    }
    if (!waypoints.isEmpty()) {
      skipCoords.put(bpmnElement, waypoints);
    }
  }
}
