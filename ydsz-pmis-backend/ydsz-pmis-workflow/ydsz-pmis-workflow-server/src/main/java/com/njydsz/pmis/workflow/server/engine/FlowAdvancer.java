package com.njydsz.pmis.workflow.server.engine;

import java.util.List;
import java.util.Map;

import com.njydsz.pmis.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.server.service.FlowInstanceService;
import com.njydsz.pmis.workflow.server.service.FlowRoutingService;

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
    FlowInstanceViewDTO start(String instanceId);

    /**
     * 完成任务后推进：找下一节点，生成下一批任务
     *
     * @param currentInstance  当前实例
     * @param currentNodeCode  当前完成的节点编码
     * @param skipType         跳转类型 PASS/REJECT
     * @param targetNodeCode   退回目标节点（REJECT 时使用，单节点）
     * @param variables        流程变量（用于条件/办理人解析）
     * @return 推进后产生的下一节点列表（空表示流程结束）
     */
    List<FlowNodeDO> advance(FlowInstanceDO currentInstance,
                              String currentNodeCode,
                              String skipType,
                              String targetNodeCode,
                              Map<String, Object> variables);

    /**
     * GAP-P0-2: 完成任务后推进（支持退回多节点同退）
     *
     * <p>对标飞书"退回多节点同退"。当 skipType=REJECT 且 targetNodeCodes 非空时，
     * 在所有指定节点同时创建待办任务，让多个前序节点重新审批。
     *
     * <p>注意：本方法特意命名为 {@code advanceMulti} 而非 {@code advance} 重载，
     * 以避免调用方传 {@code null} 时与 {@link #advance(FlowInstanceDO, String, String, String, Map)}
     * 产生重载歧义（Java 规范下 {@code null} 同时匹配 String 与 List&lt;String&gt;）。
     *
     * @param currentInstance  当前实例
     * @param currentNodeCode  当前完成的节点编码
     * @param skipType         跳转类型 PASS/REJECT
     * @param targetNodeCodes  退回多节点目标列表（REJECT 时使用，非空时优先于单节点）
     * @param variables        流程变量
     * @return 推进后产生的下一节点列表（空表示流程结束）
     * @since 1.6.0
     */
    default List<FlowNodeDO> advanceMulti(FlowInstanceDO currentInstance,
                                           String currentNodeCode,
                                           String skipType,
                                           List<String> targetNodeCodes,
                                           Map<String, Object> variables) {
        // 默认实现：降级到单节点退回（取第一个或 null）
        String single = (targetNodeCodes == null || targetNodeCodes.isEmpty())
                ? null : targetNodeCodes.get(0);
        return advance(currentInstance, currentNodeCode, skipType, single, variables);
    }

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
    String resolveRejectTarget(String definitionId, String currentNodeCode);

    /**
     * 暴露 instanceService 供外部触发（如定时器触发后需要 generateTasksForNodes）
     *
     * @return 流程实例服务
     */
    FlowInstanceService getInstanceService();
}
