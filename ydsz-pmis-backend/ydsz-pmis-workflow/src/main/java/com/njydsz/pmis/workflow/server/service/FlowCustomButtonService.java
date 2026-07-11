package com.njydsz.pmis.workflow.server.service.definition;

import java.util.List;
import java.util.Map;

/**
 * 节点自定义按钮服务（P2-4）。
 *
 * <p>对标钉钉/飞书审批的"自定义按钮"能力，允许流程设计者为特定节点配置
 * 额外的操作按钮（如"退回修改"、"补充资料"、"发起沟通"），
 * 前端按节点渲染按钮，点击后回调后端执行对应操作。
 *
 * <p>按钮配置存储在 {@code FlowNodeDO.ext} JSON 的 {@code customButtons} 字段，
 * 格式为：
 * <pre>
 * "customButtons": [
 *   {
 *     "code": "RETURN_MODIFY",
 *     "label": "退回修改",
 *     "action": "REJECT",
 *     "targetNodeCode": "fill_form",
 *     "confirmText": "确定退回修改吗？",
 *     "icon": "rollback",
 *     "sortNum": 1
 *   }
 * ]
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
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
