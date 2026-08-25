package com.njydsz.workflow.server.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.njydsz.workflow.domain.repository.FlowDefinitionRepository;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.server.engine.BpmnDiagramParser;
import com.njydsz.workflow.server.engine.BpmnModel.NodeCoordinate;
import com.njydsz.workflow.server.service.FlowChartService;

/**
 * 流程图渲染服务实现（P2-4）。
 *
 * <p>基于 BPMNDI 解析的节点/边坐标生成内联 SVG；无 BPMNDI 时使用简单自上而下自动布局。
 * 节点按状态着色：已完成=绿色、当前=蓝色、未到达=灰色。
 *
 * <p>PNG 依赖外部渲染能力（如 Batik / Playwright），本接口返回 null，由业务系统按需集成。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowChartService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowChartServiceImpl implements FlowChartService {

  /** 节点默认宽度 */
  private static final double NODE_WIDTH = 120;
  /** 节点默认高度 */
  private static final double NODE_HEIGHT = 50;
  /** 节点间距（水平） */
  private static final double NODE_GAP_X = 60;
  /** 节点间距（垂直） */
  private static final double NODE_GAP_Y = 80;
  /** 绘图边距 */
  private static final double PADDING = 40;

  /** 已完成节点颜色 */
  private static final String COLOR_DONE = "#52c41a";
  /** 当前节点颜色 */
  private static final String COLOR_ACTIVE = "#1890ff";
  /** 未到达节点颜色 */
  private static final String COLOR_PENDING = "#d9d9d9";
  /** 边框颜色 */
  private static final String COLOR_BORDER = "#bfbfbf";
  /** 当前节点边框颜色 */
  private static final String COLOR_BORDER_ACTIVE = "#1890ff";

  private final FlowDefinitionRepository definitionRepository;
  private final BpmnDiagramParser diagramParser;

  @Override
  public String generateSvg(
      String definitionId,
      Set<String> activeNodeCodes,
      Set<String> doneNodeCodes) {
    if (definitionId == null || definitionId.isBlank()) {
      log.warn("[Flow][Chart] definitionId 为空");
      return "";
    }
    Set<String> active = activeNodeCodes != null ? activeNodeCodes : Set.of();
    Set<String> done = doneNodeCodes != null ? doneNodeCodes : Set.of();

    FlowDefinitionVO def = definitionRepository.findById(definitionId).orElse(null);
    if (def == null) {
      log.warn("[Flow][Chart] 流程定义不存在: {}", definitionId);
      return "";
    }

    Map<String, NodeCoordinate> nodeCoords = new HashMap<>();
    Map<String, List<NodeCoordinate>> skipCoords = new HashMap<>();

    // 解析 BPMNDI 获取坐标
    if (def.getBpmnXml() != null && !def.getBpmnXml().isBlank()) {
      parseBpmnCoordinates(def.getBpmnXml(), nodeCoords, skipCoords);
    }

    // 如果 BPMNDI 没有坐标，构建基于 node 列表的自动布局
    if (nodeCoords.isEmpty() && def.getNodes() != null) {
      buildAutoLayout(def.getNodes(), nodeCoords);
    }

    if (nodeCoords.isEmpty()) {
      log.info("[Flow][Chart] 无可渲染的节点坐标: definitionId={}", definitionId);
      return "";
    }

    return renderSvg(nodeCoords, skipCoords, active, done);
  }

  @Override
  public byte[] generatePng(
      String definitionId,
      Set<String> activeNodeCodes,
      Set<String> doneNodeCodes) {
    // PNG 渲染依赖外部 SVG→PNG 转换器（如 Batik / Playwright），引擎不内置
    // 业务系统可基于 generateSvg() 产出 SVG 后自行转换
    log.info("[Flow][Chart] PNG 渲染需业务方集成 SVG 转换器: definitionId={}", definitionId);
    return null;
  }

  // ============================== 私有方法 ==============================

  /**
   * 解析 BPMN XML 获取节点/边坐标。
   *
   * @param bpmnXml     BPMN XML 内容
   * @param nodeCoords  输出：节点坐标
   * @param skipCoords  输出：边坐标
   */
  private void parseBpmnCoordinates(
      String bpmnXml,
      Map<String, NodeCoordinate> nodeCoords,
      Map<String, List<NodeCoordinate>> skipCoords) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new java.io.StringReader(bpmnXml)));
      diagramParser.parseBpmnDiagram(doc.getDocumentElement(), nodeCoords, skipCoords);
    } catch (Exception e) {
      log.warn("[Flow][Chart] BPMN 坐标解析失败: err={}", e.getMessage());
    }
  }

  /**
   * 自动布局：基于节点 JSON 自上而下排列。
   *
   * @param nodes 节点 JSON 列表
   * @param nodeCoords 输出：节点坐标
   */
  @SuppressWarnings("unchecked")
  private void buildAutoLayout(List<?> nodes, Map<String, NodeCoordinate> nodeCoords) {
    double x = PADDING;
    double y = PADDING;
    int count = 0;
    int nodesPerRow = 3;
    for (Object nodeObj : nodes) {
      if (!(nodeObj instanceof Map)) {
        continue;
      }
      Map<String, Object> nodeMap = (Map<String, Object>) nodeObj;
      Object codeObj = nodeMap.get("nodeCode");
      if (codeObj == null) {
        continue;
      }
      String nodeCode = String.valueOf(codeObj);
      double dx = count % nodesPerRow * (NODE_WIDTH + NODE_GAP_X);
      double dy = count / nodesPerRow * (NODE_HEIGHT + NODE_GAP_Y);
      nodeCoords.put(nodeCode, new NodeCoordinate(dx + PADDING, dy + NODE_GAP_Y, NODE_WIDTH, NODE_HEIGHT));
      count++;
    }
  }

  /**
   * 渲染 SVG。
   *
   * @param nodeCoords 节点坐标映射
   * @param skipCoords 边坐标映射
   * @param activeNodes 当前节点编码集合
   * @param doneNodes   已完成节点编码集合
   * @return SVG 字符串
   */
  private String renderSvg(
      Map<String, NodeCoordinate> nodeCoords,
      Map<String, List<NodeCoordinate>> skipCoords,
      Set<String> activeNodes,
      Set<String> doneNodes) {
    double maxX = 0;
    double maxY = 0;
    for (NodeCoordinate c : nodeCoords.values()) {
      maxX = Math.max(maxX, c.getX() + c.getWidth());
      maxY = Math.max(maxY, c.getY() + c.getHeight());
    }
    int svgWidth = (int) (maxX + PADDING);
    int svgHeight = (int) (maxY + PADDING);
    if (svgWidth <= 0 || svgHeight <= 0) {
      svgWidth = 400;
      svgHeight = 300;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append(String.format(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">%n",
        svgWidth, svgHeight, svgWidth, svgHeight));
    sb.append(String.format(
        "<rect width=\"%d\" height=\"%d\" fill=\"#fafafa\"/>%n", svgWidth, svgHeight));

    // 绘制边（skip/flow）
    for (Map.Entry<String, List<NodeCoordinate>> entry : skipCoords.entrySet()) {
      List<NodeCoordinate> waypoints = entry.getValue();
      if (waypoints == null || waypoints.size() < 2) {
        continue;
      }
      StringBuilder path = new StringBuilder();
      for (int i = 0; i < waypoints.size(); i++) {
        NodeCoordinate wp = waypoints.get(i);
        double px = wp.getX() + wp.getWidth() / 2;
        double py = wp.getY() + wp.getHeight() / 2;
        path.append(i == 0 ? "M" : "L").append(String.format("%.1f,%.1f ", px, py));
      }
      sb.append(String.format(
          "<path d=\"%s\" stroke=\"%s\" stroke-width=\"1.5\" fill=\"none\" marker-end=\"url(#arrow)\"/>%n",
          path.toString().trim(), COLOR_BORDER));
    }

    // 绘制节点
    for (Map.Entry<String, NodeCoordinate> entry : nodeCoords.entrySet()) {
      String nodeCode = entry.getKey();
      NodeCoordinate c = entry.getValue();
      String fill;
      String stroke;
      int strokeWidth;
      if (activeNodes.contains(nodeCode)) {
        fill = COLOR_ACTIVE;
        stroke = COLOR_BORDER_ACTIVE;
        strokeWidth = 2;
      } else if (doneNodes.contains(nodeCode)) {
        fill = COLOR_DONE;
        stroke = COLOR_DONE;
        strokeWidth = 1;
      } else {
        fill = COLOR_PENDING;
        stroke = COLOR_BORDER;
        strokeWidth = 1;
      }
      double rx = c.getX();
      double ry = c.getY();
      double rw = c.getWidth() > 0 ? c.getWidth() : NODE_WIDTH;
      double rh = c.getHeight() > 0 ? c.getHeight() : NODE_HEIGHT;
      // 圆角矩形
      sb.append(String.format(
          "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"6\" ry=\"6\" "
              + "fill=\"%s\" stroke=\"%s\" stroke-width=\"%d\"/>%n",
          rx, ry, rw, rh, fill, stroke, strokeWidth));
      // 节点文本（简化：仅显示 nodeCode 前 10 字符）
      String label = nodeCode.length() > 10 ? nodeCode.substring(0, 10) + "…" : nodeCode;
      sb.append(String.format(
          "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" font-size=\"11\" "
              + "fill=\"%s\" font-family=\"sans-serif\">%s</text>%n",
          rx + rw / 2, ry + rh / 2 + 4,
          (activeNodes.contains(nodeCode) || doneNodes.contains(nodeCode)) ? "#fff" : "#595959",
          escapeXml(label)));
    }

    // 箭头标记
    sb.append("<defs><marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" "
        + "markerWidth=\"6\" markerHeight=\"6\" orient=\"auto-start-reverse\">"
        + "<path d=\"M0,0 L10,5 L0,10 z\" fill=\"" + COLOR_BORDER + "\"/>"
        + "</marker></defs>%n");
    sb.append("</svg>");
    return sb.toString();
  }

  /**
   * XML 转义（防止节点名称含特殊字符导致 SVG 解析失败）。
   *
   * @param text 原始文本
   * @return 转义后文本
   */
  private String escapeXml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
