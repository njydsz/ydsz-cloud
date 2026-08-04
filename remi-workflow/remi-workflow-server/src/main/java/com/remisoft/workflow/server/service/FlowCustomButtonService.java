package com.remisoft.workflow.server.service;

import java.util.List;
import java.util.Map;

/**
 * 流程自定义按钮服务。
 * <p>在审批面板注入业务按钮。
 *
 * @author remi-team
 * @since 1.0.0
 */


public interface FlowCustomButtonService {

    /**
     * 获取节点的自定义按钮列表。
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @return 自定义按钮列表（按 sortNum 排序）
     */
    List<Map<String, Object>> getCustomButtons(String definitionId, String nodeCode);

    /**
     * 保存节点的自定义按钮配置。
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param buttons      按钮配置列表
     */
    void saveCustomButtons(String definitionId, String nodeCode, List<Map<String, Object>> buttons);

    /**
     * 执行自定义按钮操作。
     *
     * <p>根据按钮配置的 action 类型路由到对应的工作流操作：
     * <ul>
     *   <li>PASS — 调用任务通过</li>
     *   <li>REJECT — 调用任务驳回（带 targetNodeCode）</li>
     *   <li>TRANSFER — 调用任务转办</li>
     *   <li>DELEGATE — 调用任务委派</li>
     *   <li>CUSTOM — 调用自定义回调 URL</li>
     * </ul>
     *
     * @param taskId    任务 ID
     * @param buttonCode 按钮编码
     * @param userId    操作人 ID
     * @param comment   审批意见
     * @param variables 附加变量
     * @return 执行结果
     */
    Map<String, Object> executeButton(String taskId, String buttonCode,
                                       String userId, String comment,
                                       Map<String, Object> variables);
}
