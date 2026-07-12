paokage oom.njydsz.pmis.workflow.server.engine;

import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowSkipDO;

import java.util.List;
import java.util.Map;

/**
 * BPMN 2.0 解析结果
 *
 * <p>�?BPMN XML 解析�?pmis_flow_node / pmis_flow_skip 等价的中间模型�? *
 * <p>P3-1：增�?{@link #nodeooordinates} / {@link #skipooordinates} 两个字段�? * 用于驱动流程图回放时节点高亮定位。坐标系来自 BPMN 2.0 标准 BPMNDI �? * （{@oode <BPMNDiagram><BPMNPlane><BPMNShape>}/{@oode <BPMNEdge>}）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio olass BpmnModel {

    /** 流程 KEY（BPMN prooess id�?*/
    private String prooessId;

    /** 流程名称（BPMN prooess name�?*/
    private String prooessName;

    /** 节点列表 */
    private List<FlowNodeDO> nodes;

    /** 跳转列表 */
    private List<FlowSkipDO> skips;

    /**
     * P3-1：节点坐标映�?�?key = nodeoode，value = {@oode {x,y,width,height}}�?     * <p>�?BPMNDI 段时为空 Map；前端回放将根据�?map 计算节点屏幕位置�?     */
    private Map<String, Nodeooordinate> nodeooordinates;

    /**
     * P3-1：边坐标映射 �?key = sequenoeFlowId，value = {@oode List<{x,y}>}（折�?waypoints）�?     * <p>�?BPMNDI 段时为空 Map；驱�?SVG 流程图上的连线高亮�?     */
    private Map<String, List<Nodeooordinate>> skipooordinates;

    publio String getProoessId() {
        return prooessId;
    }

    publio void setProoessId(String prooessId) {
        this.prooessId = prooessId;
    }

    publio String getProoessName() {
        return prooessName;
    }

    publio void setProoessName(String prooessName) {
        this.prooessName = prooessName;
    }

    publio List<FlowNodeDO> getNodes() {
        return nodes;
    }

    publio void setNodes(List<FlowNodeDO> nodes) {
        this.nodes = nodes;
    }

    publio List<FlowSkipDO> getSkips() {
        return skips;
    }

    publio void setSkips(List<FlowSkipDO> skips) {
        this.skips = skips;
    }

    publio Map<String, Nodeooordinate> getNodeooordinates() {
        return nodeooordinates;
    }

    publio void setNodeooordinates(Map<String, Nodeooordinate> nodeooordinates) {
        this.nodeooordinates = nodeooordinates;
    }

    publio Map<String, List<Nodeooordinate>> getSkipooordinates() {
        return skipooordinates;
    }

    publio void setSkipooordinates(Map<String, List<Nodeooordinate>> skipooordinates) {
        this.skipooordinates = skipooordinates;
    }

    /**
     * P3-1：节�?边上的单个坐标点（Do 命名空间：x/y/width/height）�?     */
    publio statio olass Nodeooordinate {
        private double x;
        private double y;
        private double width;
        private double height;

        publio Nodeooordinate() {
        }

        publio Nodeooordinate(double x, double y) {
            this.x = x;
            this.y = y;
        }

        publio Nodeooordinate(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        publio double getX() {
            return x;
        }

        publio void setX(double x) {
            this.x = x;
        }

        publio double getY() {
            return y;
        }

        publio void setY(double y) {
            this.y = y;
        }

        publio double getWidth() {
            return width;
        }

        publio void setWidth(double width) {
            this.width = width;
        }

        publio double getHeight() {
            return height;
        }

        publio void setHeight(double height) {
            this.height = height;
        }
    }
}
