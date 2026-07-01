package com.njydsz.pmis.workflow.flow.engine;

import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;

import java.util.List;

/**
 * BPMN 2.0 解析结果
 *
 * <p>将 BPMN XML 解析为 pmis_flow_node / pmis_flow_skip 等价的中间模型。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class BpmnModel {

    /** 流程 KEY（BPMN process id） */
    private String processId;

    /** 流程名称（BPMN process name） */
    private String processName;

    /** 节点列表 */
    private List<FlowNodeDO> nodes;

    /** 跳转列表 */
    private List<FlowSkipDO> skips;

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

    public List<FlowNodeDO> getNodes() {
        return nodes;
    }

    public void setNodes(List<FlowNodeDO> nodes) {
        this.nodes = nodes;
    }

    public List<FlowSkipDO> getSkips() {
        return skips;
    }

    public void setSkips(List<FlowSkipDO> skips) {
        this.skips = skips;
    }
}
