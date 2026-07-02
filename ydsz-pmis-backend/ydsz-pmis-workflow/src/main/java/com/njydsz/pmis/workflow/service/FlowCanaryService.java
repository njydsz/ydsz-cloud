package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;

import java.util.List;
import java.util.Map;

/**
 * 灰度发布服务
 *
 * <p>P3-1 落地：支持流程定义的 canary release（灰度发布）能力。
 *
 * <p>核心能力：
 * <ul>
 *   <li>{@link #publishCanary} — 将指定定义标记为灰度版，按初始比例切流</li>
 *   <li>{@link #adjustCanaryPercent} — 调整灰度比例（逐步放量）</li>
 *   <li>{@link #promoteCanary} — 全量发布：灰度版晋升为稳定版</li>
 *   <li>{@link #rollbackCanary} — 回滚：灰度版失效，强制回退到稳定版</li>
 *   <li>{@link #resolveEffectiveDefinition} — 启动流程时根据 canary 配置解析实际生效的版本</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
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
    void publishCanary(Long definitionId, int initialPercent, String strategy,
                       Long operatorId, String operatorName, String note);

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
    void adjustCanaryPercent(Long definitionId, int newPercent,
                             Long operatorId, String operatorName, String note);

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
    void promoteCanary(Long definitionId, Long operatorId, String operatorName, String note);

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
    void rollbackCanary(Long definitionId, Long operatorId, String operatorName, String note);

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
    FlowDefinitionDO resolveEffectiveDefinition(String flowCode, String version,
                                                Long tenantId, Long initiatorId);

    /**
     * 查询某 flowCode 的灰度发布历史
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID
     * @return rollout 日志列表（按 operateAt 升序）
     */
    List<Map<String, Object>> listCanaryRolloutLog(String flowCode, Long tenantId);
}
