package com.njydsz.pmis.workflow.server.service;

import java.util.List;
import java.util.Map;

import com.njydsz.pmis.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;

/**
 * 工作流子流程（CallActivity / SubProcess）服务
 *
 * <p>P1-3: 子流程运行时。
 *
 * <p>CallActivity 节点触发时调用 {@link #startSubProcess} 创建子实例，
 * 子实例完成后通过 onInstanceCompleted 事件回调 {@link #onSubProcessCompleted}
 * 推进父流程。
 *
 * <p>设计原则：
 * <ul>
 *   <li>父流程停在 callActivity 节点（不生成新待办）</li>
 *   <li>子流程独立运行，与父流程业务关联（businessType/businessId 可不同）</li>
 *   <li>子流程完成后自动推进父流程到下一节点</li>
 *   <li>子流程驳回/终止：父流程同步驳回/终止</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface FlowSubProcessService {

    /**
     * 启动子流程实例（callActivity 节点触发）
     *
     * @param parentInstance   父流程实例
     * @param callActivityNode callActivity 节点（其 ext.callActivityFlowCode 标记子流程编码）
     * @param variables        父流程变量（传递给子流程）
     * @return 子流程实例 ID
     */
    String startSubProcess(FlowInstanceDO parentInstance,
                         FlowNodeDO callActivityNode,
                         Map<String, Object> variables);

    /**
     * 子流程完成事件回调（由 ProjectInitiationFlowListener 调用）
     *
     * @param childInstanceId 子流程实例 ID
     */
    void onSubProcessCompleted(String childInstanceId);

    /**
     * 子流程驳回/终止事件回调（同步父流程）
     *
     * @param childInstanceId 子流程实例 ID
     * @param reason          原因
     * @param terminal        true=终止父流程；false=驳回父流程到 callActivity 节点
     */
    void onSubProcessTerminated(String childInstanceId, String reason, boolean terminal);

    /**
     * 查询父流程的所有子流程实例
     *
     * @param parentInstanceId 父流程实例 ID
     * @return 子流程实例列表
     */
    List<FlowInstanceDO> listChildren(String parentInstanceId);

    /** DTO 构造工具：把子流程启动所需参数封装 */
    FlowStartProcessDTO buildSubProcessStartDTO(FlowInstanceDO parentInstance,
                                                String subFlowCode,
                                                Map<String, Object> variables);

    /**
     * 获取子流程完整上下文（父流程变量 + 子流程自身变量）
     *
     * @param childInstanceId 子流程实例 ID
     * @return 合并后的变量 Map
     */
    Map<String, Object> getSubProcessContext(String childInstanceId);

    /**
     * 递归查询子流程树
     *
     * @param parentInstanceId 父流程实例 ID
     * @return 子流程树列表，格式 [{instanceId, instanceName, flowCode, status, subProcesses: [...]}]
     */
    List<Map<String, Object>> listSubProcessTree(String parentInstanceId);
}
