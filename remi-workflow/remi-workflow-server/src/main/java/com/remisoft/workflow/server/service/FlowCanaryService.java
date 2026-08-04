package com.remisoft.workflow.server.service;

import java.util.List;
import java.util.Map;

import com.remisoft.workflow.domain.entity.FlowDefinition;

/**
 * 流程灰度服务。
 * <p>按部门/角色/租户灰度发布新版本流程。
 *
 * @author remi-team
 * @since 1.0.0
 */


public interface FlowCanaryService {

    /**
     * 启动灰度：将指定定义标记为灰度版，按初始比例切流
     *
     * <p>要求定义已发布（isPublish=1）且尚未处于灰度状态。灰度开始时，
     * 当前稳定版仍可继续接收流量；按 initialPercent 切流到灰度版。
     *
     * @param definitionId   要灰度的定义 ID
     * @param initialPercent 初始灰度比例（0-100），建议从 1-10 开始
     * @param strategy       切流策略：USER_HASH / RANDOM / WHITELIST
     * @param operatorId     操作人 ID
     * @param operatorName   操作人姓名
     * @param note           备注
     */
    void publishCanary(String definitionId, int initialPercent, String strategy,
                       String operatorId, String operatorName, String note);

    /**
     * 调整灰度比例：在 1-99 范围内逐步放量或缩量。
     *
     * <p>仅当 canaryStatus=CANARYING 时允许调整。调整后追加一条 rollout log。
     *
     * @param definitionId 定义 ID
     * @param newPercent   新比例（1-99）
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     * @param note         备注
     */
    void adjustCanaryPercent(String definitionId, int newPercent,
                             String operatorId, String operatorName, String note);

    /**
     * 全量发布：灰度版晋升为稳定版
     *
     * <p>将当前 isPublish=1 的其他版本自动失效（仅同 flowCode + tenant 范围内）。
     * canaryPercent=100，canaryStatus=PROMOTED。
     *
     * @param definitionId 灰度版定义 ID
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     * @param note         备注
     */
    void promoteCanary(String definitionId, String operatorId, String operatorName, String note);

    /**
     * 回滚：灰度版失效
     *
     * <p>将灰度版 isPublish=9，canaryStatus=ROLLED_BACK，canaryPercent=0。
     * 后续所有请求都走稳定版。
     *
     * @param definitionId 灰度版定义 ID
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     * @param note         备注（含回滚原因）
     */
    void rollbackCanary(String definitionId, String operatorId, String operatorName, String note);

    /**
     * 解析流程启动时实际生效的版本
     *
     * <p>核心切流逻辑：根据稳定版 + 灰度版 + canaryPercent + canaryStrategy + 发起人ID，
     * 决定本次启动应该使用哪个版本的定义。
     *
     * @param flowCode   流程编码
     * @param version    请求的版本（可选）
     * @param tenantId   租户 ID
     * @param initiatorId 发起人 ID
     * @return 实际生效的定义（含切流结果），无灰度时返回原稳定版
     */
    FlowDefinition resolveEffectiveDefinition(String flowCode, String version,
                                                String tenantId, String initiatorId);

    /**
     * 查询某 flowCode 的灰度发布历史
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID
     * @return rollout 日志列表（按 operateAt 升序）
     */
    List<Map<String, Object>> listCanaryRolloutLog(String flowCode, String tenantId);
}