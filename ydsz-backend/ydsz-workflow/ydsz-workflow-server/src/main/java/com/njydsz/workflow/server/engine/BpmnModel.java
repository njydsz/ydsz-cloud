package com.njydsz.workflow.server.engine;

import java.util.List;
import java.util.Map;

import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowSkip;

/**
 * BPMN 2.0 解析结果
 *
 * <p>将 BPMN XML 解析为 ydsz_flow_node / ydsz_flow_skip 等价的中间模型。
 *
 * <p>P3-1：增加 {@link #nodeCoordinates} / {@link #skipCoordinates} 两个字段，
 * 用于驱动流程图回放时节点高亮定位。坐标系来自 BPMN 2.0 标准 BPMNDI 段
 * （{@code <BPMNDiagram><BPMNPlane><BPMNShape>}/{@code <BPMNEdge>}）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class BpmnModel {

    /** 流程 KEY（BPMN process id） */
    private String processId;

    /** 流程名称（BPMN process name） */
    private String processName;

    /** 节点列表 */
    private List<FlowNode> nodes;

    /** 跳转列表 */
    private List<FlowSkip> skips;

    /**
     * P3-1：节点坐标映射 — key = nodeCode，value = {@code {x,y,width,height}}。
     * <p>无 BPMNDI 段时为空 Map；前端回放将根据此 map 计算节点屏幕位置。
     */
    private Map<String, NodeCoordinate> nodeCoordinates;

    /**
     * P3-1：边坐标映射 — key = sequenceFlowId，value = {@code List<{x,y}>}（折线 waypoints）。
     * <p>无 BPMNDI 段时为空 Map；驱动 SVG 流程图上的连线高亮。
     */
    private Map<String, List<NodeCoordinate>> skipCoordinates;

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public List<FlowNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<FlowNode> nodes) {
        this.nodes = nodes;
    }

    public List<FlowSkip> getSkips() {
        return skips;
    }

    public void setSkips(List<FlowSkip> skips) {
        this.skips = skips;
    }

    public Map<String, NodeCoordinate> getNodeCoordinates() {
        return nodeCoordinates;
    }

    public void setNodeCoordinates(Map<String, NodeCoordinate> nodeCoordinates) {
        this.nodeCoordinates = nodeCoordinates;
    }

    public Map<String, List<NodeCoordinate>> getSkipCoordinates() {
        return skipCoordinates;
    }

    public void setSkipCoordinates(Map<String, List<NodeCoordinate>> skipCoordinates) {
        this.skipCoordinates = skipCoordinates;
    }

    /**
     * P3-1：节点/边上的单个坐标点（DC 命名空间：x/y/width/height）。
     */
    public static class NodeCoordinate {
        private double x;
        private double y;
        private double width;
        private double height;

        public NodeCoordinate() {
        }

        public NodeCoordinate(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public NodeCoordinate(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getWidth() {
            return width;
        }

        public void setWidth(double width) {
            this.width = width;
        }

        public double getHeight() {
            return height;
        }

        public void setHeight(double height) {
            this.height = height;
        }
    }
}
