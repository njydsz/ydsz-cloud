package com.njydsz.pmis.common.featureflag;

import java.util.List;
import java.util.Map;

/**
 * 特性开关服务接口 (批次 20 P2-3)
 *
 * <p>提供 flag 状态查询 / 灰度发布 / admin 控制的核心方法.
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * @Autowired
 * private FeatureFlagService featureFlags;
 *
 * if (featureFlags.isEnabled(FeatureFlag.AGENT_ORCHESTRATION)) {
 *     // 展示多智能体编排菜单
 * }
 *
 * // 带用户维度的灰度发布判断
 * if (featureFlags.isEnabled(FeatureFlag.COCKPIT_V2, currentUserId)) {
 *     return new CockpitV2Dashboard();
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
public interface FeatureFlagService {

    /** config 中 feature flag 所在分组 */
    String CONFIG_GROUP = "feature_flag";

    /**
     * 通用判断: 某 flag 是否启用 (不带用户维度, 即 rolloutPercentage=100 时才返回 true).
     */
    default boolean isEnabled(FeatureFlag flag) {
        return isEnabled(flag, null);
    }

    /**
     * 通用判断: 某 flag 是否启用 (可指定用户做灰度).
     *
     * @param flag   特性开关
     * @param userId 用户 ID, 为 null 时不应用灰度比例 (rolloutPercentage=100 才算开启)
     * @return true 表示启用
     */
    boolean isEnabled(FeatureFlag flag, Long userId);

    /**
     * 获取所有 flag 的快照.
     */
    List<FeatureFlagSnapshot> snapshot();

    /**
     * 按分类聚合快照.
     */
    Map<String, List<FeatureFlagSnapshot>> snapshotByCategory();

    /**
     * Admin: 设置某 flag 的开关值 (写入 config 中心).
     *
     * @param flag    特性开关
     * @param enabled 启用/禁用
     * @return 实际生效值 (mandatory 永远是 true)
     */
    boolean setEnabled(FeatureFlag flag, boolean enabled);

    /**
     * Admin: 设置灰度发布比例 0-100.
     */
    int setRolloutPercentage(FeatureFlag flag, int percentage);

    /**
     * 强制刷新本地缓存 (config 更新后调用, 立即生效).
     */
    void refresh();
}
