package com.njydsz.workflow.server.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.server.engine.BpmnModel.NodeCoordinate;
import com.njydsz.workflow.server.service.FlowChartService;

/**
 * 流程图渲染服务实现（P2-4）。
 *
 * <p>基于节点 {@code coordinate} 字段（bpmn-js 设计器坐标 JSON）生成内联 SVG；
 * 无坐标时使用简单自上而下自动布局。节点按状态着色：已完成=绿色、当前=蓝色、未到达=灰色。
 *
 * <p>PNG 依赖外部渲染能力（如 Batik / Playwright），本接口返回 null，由业务系统按需集成。
 *
 * @author ydsz-team
 * @since 26.09.01
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

  /** 自动布局：每行节点数 */
  private static final int NODES_PER_ROW = 3;

  /** 无坐标兜底画布最小宽/高 */
  private static final int FALLBACK_SVG_WIDTH = 400;
  private static final int FALLBACK_SVG_HEIGHT = 300;

  /** 文本垂直偏移（相对节点中心） */
  private static final int TEXT_V_OFFSET = 4;

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

  private final FlowNodeRepository nodeRepository;

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

    List<FlowNodeVO> nodes = nodeRepository.findByDefinitionId(definitionId);
    if (nodes == null || nodes.isEmpty()) {
      log.info("[Flow][Chart] 流程定义无节点: definitionId={}", definitionId);
      return "";
    }

    Map<String, NodeCoordinate> nodeCoords = new HashMap<>(nodes.size() * 2);
    for (FlowNodeVO node : nodes) {
      String nodeCode = node.getNodeCode();
      String coordinate = node.getCoordinate();
      if (coordinate != null && !coordinate.isBlank()) {
        try {
          Map<String, Object> coordMap = YdszJson.fromJsonToMap(coordinate, String.class, Object.class);
          if (coordMap != null && coordMap.containsKey("x") && coordMap.containsKey("y")) {
            double x = parseDouble(coordMap.get("x"));
            double y = parseDouble(coordMap.get("y"));
            double w = coordMap.containsKey("width") ? parseDouble(coordMap.get("width")) : NODE_WIDTH;
            double h = coordMap.containsKey("height") ? parseDouble(coordMap.get("height")) : NODE_HEIGHT;
            nodeCoords.put(nodeCode, new NodeCoordinate(x, y, w, h));
          }
        } catch (Exception e) {
          log.debug("[Flow][Chart] 节点坐标解析失败: nodeCode={} err={}", nodeCode, e.getMessage());
        }
      }
    }

    // 无坐标节点使用自动布局
    if (nodeCoords.isEmpty()) {
      double x = PADDING;
      double y = PADDING;
      int count = 0;
      int nodesPerRow = NODES_PER_ROW;
      for (FlowNodeVO node : nodes) {
        String nodeCode = node.getNodeCode();
        if (nodeCode == null) {
          continue;
        }
        double dx = count % nodesPerRow * (NODE_WIDTH + NODE_GAP_X);
        double dy = count / nodesPerRow * (NODE_HEIGHT + NODE_GAP_Y);
        nodeCoords.put(nodeCode, new NodeCoordinate(dx + PADDING, dy + NODE_GAP_Y, NODE_WIDTH, NODE_HEIGHT));
        count++;
      }
    }

    return renderSvg(nodeCoords, active, done);
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
   * 渲染 SVG。
   *
   * @param nodeCoords  节点坐标映射
   * @param activeNodes 当前节点编码集合
   * @param doneNodes   已完成节点编码集合
   * @return SVG 字符串
   */
  private String renderSvg(
      Map<String, NodeCoordinate> nodeCoords,
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
      svgWidth = FALLBACK_SVG_WIDTH;
      svgHeight = FALLBACK_SVG_HEIGHT;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append(String.format(
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">%n",
        svgWidth, svgHeight, svgWidth, svgHeight));
    sb.append(String.format(
        "<rect width=\"%d\" height=\"%d\" fill=\"#fafafa\"/>%n", svgWidth, svgHeight));

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
          rx + rw / 2, ry + rh / 2 + TEXT_V_OFFSET,
          (activeNodes.contains(nodeCode) || doneNodes.contains(nodeCode)) ? "#fff" : "#595959",
          escapeXml(label)));
    }
    sb.append("</svg>");
    return sb.toString();
  }

  /**
   * 解析数值（兼容 Integer/Long/Double/String）。
   *
   * @param obj 原始值
   * @return double 值，解析失败返回 0
   */
  private double parseDouble(Object obj) {
    if (obj == null) {
      return 0;
    }
    if (obj instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(obj));
    } catch (NumberFormatException e) {
      return 0;
    }
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
