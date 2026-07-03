package com.njydsz.pmis.workflow.engine;

import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowRoutingService;

import java.util.List;
import java.util.Map;

/**
 * 流程推进器：状态机核心
 *
 * <p>负责：找下一节点 → 生成任务 → 更新实例状态。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowAdvancer {

    /**
     * 启动实例：找到开始节点，生成第一批任务
     */
    FlowInstanceViewDTO start(Long instanceId);

    /**
     * 完成任务后推进：找下一节点，生成下一批任务
     *
     * @param currentInstance  当前实例
     * @param currentNodeCode  当前完成的节点编码
     * @param skipType         跳转类型 PASS/REJECT
     * @param targetNodeCode   退回目标节点（REJECT 时使用）
     * @param variables        流程变量（用于条件/办理人解析）
     * @return 推进后产生的下一节点列表（空表示流程结束）
     */
    List<FlowNodeDO> advance(FlowInstanceDO currentInstance,
                              String currentNodeCode,
                              String skipType,
                              String targetNodeCode,
                              Map<String, Object> variables);

    /**
     * 解析出所有满足条件的 PASS 跳转
     */
    List<FlowSkipDO> resolvePassSkips(FlowInstanceDO instance,
                                       FlowNodeDO currentNode,
                                       Map<String, Object> variables);

    /**
     * 评估跳转条件表达式
     *
     * <p>默认实现：条件为空时返回 true，否则委托给 {@link FlowVariableStrategy#evaluate(String, Map)}。
     * 子类可覆写以优先使用 {@link FlowRoutingService} 评估。
     *
     * @param condition 跳转条件表达式
     * @param variables 流程变量
     * @return true=条件成立，false=不成立
     * @since 1.2.0
     */
    default boolean evaluateSkipCondition(String condition, Map<String, Object> variables) {
        return condition == null || condition.isBlank();
    }

    /**
     * 解析退回时的目标节点（默认：当前节点的前驱节点）
     */
    String resolveRejectTarget(Long definitionId, String currentNodeCode);

    /**
     * 暴露 instanceService 供外部触发（如定时器触发后需要 generateTasksForNodes）
     *
     * @return 流程实例服务
     */
    FlowInstanceService getInstanceService();
}
