package com.njydsz.workflow.server.engine;

import java.util.List;
import java.util.Map;

import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;

/**
 * BPMN 2.0 解析结果
 *
 * <p>将 BPMN XML 解析为 ydsz_flow_node / ydsz_flow_skip 等价的中间模型。
 *
 * <p>P3-1：增加 {@link #nodeCoordinates} / {@link #skipCoordinates} 两个字段， 用于驱动流程图回放时节点高亮定位。坐标系来自 BPMN
 * 2.0 标准 BPMNDI 段 （{@code <BPMNDiagram><BPMNPlane><BPMNShape>}/{@code <BPMNEdge>}）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class BpmnModel {

  /** 流程 KEY（BPMN process id） */
  private String processId;

  /** 流程名称（BPMN process name） */
  private String processName;

  /** 节点列表 */
  private List<FlowNodeVO> nodes;

  /** 跳转列表 */
  private List<FlowSkipVO> skips;

  /**
   * P3-1：节点坐标映射 — key = nodeCode，value = {@code {x,y,width,height}}。
   *
   * <p>无 BPMNDI 段时为空 Map；前端回放将根据此 map 计算节点屏幕位置。
   */
  private Map<String, NodeCoordinate> nodeCoordinates;

  /**
   * P3-1：边坐标映射 — key = sequenceFlowId，value = {@code List<{x,y}>}（折线 waypoints）。
   *
   * <p>无 BPMNDI 段时为空 Map；驱动 SVG 流程图上的连线高亮。
   */
  private Map<String, List<NodeCoordinate>> skipCoordinates;

  /** @return 流程 KEY（BPMN process id） */
  public String getProcessId() {
    return processId;
  }

  /** @param processId 流程 KEY */
  public void setProcessId(String processId) {
    this.processId = processId;
  }

  /** @return 流程名称（BPMN process name） */
  public String getProcessName() {
    return processName;
  }

  /** @param processName 流程名称 */
  public void setProcessName(String processName) {
    this.processName = processName;
  }

  /** @return 节点列表 */
  public List<FlowNodeVO> getNodes() {
    return nodes;
  }

  /** @param nodes 节点列表 */
  public void setNodes(List<FlowNodeVO> nodes) {
    this.nodes = nodes;
  }

  /** @return 跳转列表 */
  public List<FlowSkipVO> getSkips() {
    return skips;
  }

  /** @param skips 跳转列表 */
  public void setSkips(List<FlowSkipVO> skips) {
    this.skips = skips;
  }

  /** @return 节点坐标映射（key = nodeCode） */
  public Map<String, NodeCoordinate> getNodeCoordinates() {
    return nodeCoordinates;
  }

  /** @param nodeCoordinates 节点坐标映射 */
  public void setNodeCoordinates(Map<String, NodeCoordinate> nodeCoordinates) {
    this.nodeCoordinates = nodeCoordinates;
  }

  /** @return 边坐标映射（key = sequenceFlowId） */
  public Map<String, List<NodeCoordinate>> getSkipCoordinates() {
    return skipCoordinates;
  }

  /** @param skipCoordinates 边坐标映射 */
  public void setSkipCoordinates(Map<String, List<NodeCoordinate>> skipCoordinates) {
    this.skipCoordinates = skipCoordinates;
  }

  /** P3-1：节点/边上的单个坐标点（DC 命名空间：x/y/width/height）。 */
  public static class NodeCoordinate {
    private double x;
    private double y;
    private double width;
    private double height;

    /** 默认构造。 */
    public NodeCoordinate() {}

    /**
     * 构造坐标点（仅 x/y，用于边 waypoint）。
     *
     * @param x 横坐标
     * @param y 纵坐标
     */
    public NodeCoordinate(double x, double y) {
      this.x = x;
      this.y = y;
    }

    /**
     * 构造节点矩形坐标（x/y + 宽高）。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @param width 宽度
     * @param height 高度
     */
    public NodeCoordinate(double x, double y, double width, double height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }

    /** @return 横坐标。 */
    public double getX() {
      return x;
    }

    /** @param x 横坐标。 */
    public void setX(double x) {
      this.x = x;
    }

    /** @return 纵坐标。 */
    public double getY() {
      return y;
    }

    /** @param y 纵坐标。 */
    public void setY(double y) {
      this.y = y;
    }

    /** @return 宽度。 */
    public double getWidth() {
      return width;
    }

    /** @param width 宽度。 */
    public void setWidth(double width) {
      this.width = width;
    }

    /** @return 高度。 */
    public double getHeight() {
      return height;
    }

    /** @param height 高度。 */
    public void setHeight(double height) {
      this.height = height;
    }
  }
}
