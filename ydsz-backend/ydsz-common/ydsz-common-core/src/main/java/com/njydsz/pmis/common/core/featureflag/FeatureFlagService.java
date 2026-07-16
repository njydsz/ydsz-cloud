package com.njydsz.common.core.featureflag;

import java.util.List;
import java.util.Map;

/**
 * 特性开关服务
 *
 * <p>提供特性开关的读取、判断、启停与灰度发布能力。具体实现可基于内存、数据库或配置中心。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FeatureFlagService {

    /**
     * 判断指定特性开关是否对指定用户启用
     *
     * @param flag   特性开关枚举
     * @param userId 用户 ID（可空，空时按全局策略判断）
     * @return true 表示特性已启用
     */
    boolean isEnabled(FeatureFlag flag, String userId);

    /**
     * 获取全部特性开关的当前快照
     *
     * @return 特性开关快照列表
     */
    List<FeatureFlagSnapshot> snapshot();

    /**
     * 按分类分组获取特性开关快照
     *
     * @return key=分类名称，value=该分类下的特性开关快照列表
     */
    Map<String, List<FeatureFlagSnapshot>> snapshotByCategory();

    /**
     * 设置特性开关的启用/禁用状态
     *
     * @param flag    特性开关枚举
     * @param enabled 是否启用
     * @return true 表示设置成功（强制开启的开关忽略禁用操作并返回当前生效值）
     */
    boolean setEnabled(FeatureFlag flag, boolean enabled);

    /**
     * 设置特性开关的灰度发布百分比
     *
     * @param flag       特性开关枚举
     * @param percentage 灰度百分比（0-100）
     * @return 影响的记录数
     */
    int setRolloutPercentage(FeatureFlag flag, int percentage);

    /**
     * 刷新特性开关缓存（从数据源重新加载）
     */
    void refresh();
}
