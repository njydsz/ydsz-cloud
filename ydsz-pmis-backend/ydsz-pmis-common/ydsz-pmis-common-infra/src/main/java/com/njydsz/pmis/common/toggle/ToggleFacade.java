package com.njydsz.pmis.common.toggle;

import com.njydsz.pmis.common.canary.CanaryContext;
import com.njydsz.pmis.common.canary.CanaryRouter;
import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 统一开关控制面门面（P0-4 架构优化）。
 *
 * <p>将灰度路由（{@link CanaryRouter}）、特性开关（{@link FeatureFlagService}）
 * 统一到一个入口，提供以下能力：
 *
 * <h3>核心方法</h3>
 * <ul>
 *   <li>{@link #isFeatureEnabled} — 特性开关查询（支持用户维度灰度）</li>
 *   <li>{@link #routeVersion} — 灰度版本路由（按模块 + 上下文选择版本）</li>
 *   <li>{@link #evaluateToggle} — 统一开关评估（先查特性开关，再查灰度版本）</li>
 * </ul>
 *
 * <p>各模块（literule、message、workflow、project）统一注入此门面，
 * 替代各自独立实现的 CanaryService / FeatureFlagService / ABTestService。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 检查特性开关
 * if (toggleFacade.isFeatureEnabled(FeatureFlag.AGENT_ORCHESTRATION, userId)) {
 *     // 启用多智能体编排
 * }
 *
 * // 2. 灰度路由
 * String version = toggleFacade.routeVersion("message", CanaryContext.of(userId));
 *
 * // 3. 统一评估
 * ToggleResult result = toggleFacade.evaluateToggle(
 *     "literule", "RULE_V2", FeatureFlag.RULE_AI_ENHANCE, userId);
 * if (result.isEnabled() && "v2".equals(result.getVersion())) {
 *     // 使用 v2 规则引擎 + AI 增强
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-4)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToggleFacade {

    private final CanaryRouter canaryRouter;
    private final FeatureFlagService featureFlagService;

    /**
     * 检查特性开关是否启用（带用户维度灰度）。
     *
     * @param flag   特性开关枚举
     * @param userId 用户 ID（可为 null，不应用灰度比例）
     * @return true 表示启用
     */
    public boolean isFeatureEnabled(FeatureFlag flag, String userId) {
        try {
            return featureFlagService.isEnabled(flag, userId);
        } catch (Exception e) {
            log.warn("[ToggleFacade] 特性开关查询失败，降级为 false: flag={} err={}",
                    flag, e.getMessage());
            return false;
        }
    }

    /**
     * 检查特性开关是否启用（不带用户维度）。
     *
     * @param flag 特性开关枚举
     * @return true 表示启用
     */
    public boolean isFeatureEnabled(FeatureFlag flag) {
        return isFeatureEnabled(flag, null);
    }

    /**
     * 灰度版本路由。
     *
     * @param module  模块名（message / literule / workflow / project）
     * @param context 灰度上下文
     * @return 选中的版本标识（如 "v1" / "v2"），无灰度配置时返回 "default"
     */
    public String routeVersion(String module, CanaryContext context) {
        try {
            return canaryRouter.route(module, context);
        } catch (Exception e) {
            log.warn("[ToggleFacade] 灰度路由失败，降级到 default: module={} err={}",
                    module, e.getMessage());
            return "default";
        }
    }

    /**
     * 统一开关评估（先查特性开关，再查灰度版本）。
     *
     * <p>评估逻辑：
     * <ol>
     *   <li>检查特性开关是否启用（带用户维度灰度）</li>
     *   <li>若启用，查询灰度路由返回的版本</li>
     *   <li>返回包含开关状态 + 版本信息的综合结果</li>
     * </ol>
     *
     * @param module    模块名
     * @param versionId 灰度版本标识（用于日志/追踪）
     * @param flag      特性开关枚举
     * @param userId    用户 ID
     * @return 统一开关评估结果
     */
    public ToggleResult evaluateToggle(String module, String versionId,
                                        FeatureFlag flag, String userId) {
        boolean enabled = isFeatureEnabled(flag, userId);
        String version = "default";
        if (enabled) {
            CanaryContext ctx = CanaryContext.builder()
                    .userId(userId)
                    .build();
            version = routeVersion(module, ctx);
        }
        return new ToggleResult(enabled, version, module, versionId, flag != null ? flag.name() : null);
    }

    /**
     * 获取所有灰度配置。
     *
     * @param module 模块名（null 表示全部）
     * @return 灰度配置列表
     */
    public java.util.List<com.njydsz.pmis.common.canary.CanaryConfig> getCanaryConfigs(String module) {
        return canaryRouter.getConfigs(module);
    }

    /**
     * 更新灰度配置。
     *
     * @param module 模块名
     * @param config 灰度配置
     */
    public void updateCanaryConfig(String module, com.njydsz.pmis.common.canary.CanaryConfig config) {
        canaryRouter.updateConfig(module, config);
    }

    /**
     * 获取特性开关快照。
     *
     * @return 快照列表
     */
    public java.util.List<com.njydsz.pmis.common.featureflag.FeatureFlagSnapshot> getFeatureFlagSnapshots() {
        return featureFlagService.snapshot();
    }

    /**
     * 刷新缓存（配置更新后调用）。
     */
    public void refresh() {
        featureFlagService.refresh();
    }

    /**
     * 统一开关评估结果。
     *
     * @param enabled    是否启用
     * @param version    灰度版本
     * @param module     模块名
     * @param versionId  版本标识
     * @param flagName   特性开关名称
     */
    public record ToggleResult(boolean enabled, String version, String module,
                                String versionId, String flagName) {
        /**
         * 是否使用指定版本
         */
        public boolean isVersion(String v) {
            return enabled && v != null && v.equals(version);
        }
    }
}
