package com.njydsz.pmis.system.server.service.impl.config;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.core.featureflag.FeatureFlag;
import com.njydsz.pmis.common.core.featureflag.FeatureFlagService;
import com.njydsz.pmis.common.core.featureflag.FeatureFlagSnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 特性开关服务实现
 *
 * <p>委托给 common 模块的 {@link FeatureFlagService} 实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagServiceImpl implements FeatureFlagService {

    private final FeatureFlagService featureFlagService;

    /**
     * 判断指定特性开关是否对指定用户启用
     *
     * @param flag   特性开关枚举
     * @param userId 用户 ID（可空，空时按全局策略判断）
     * @return true 表示特性已启用
     */
    @Override
    public boolean isEnabled(FeatureFlag flag, String userId) {
        return featureFlagService.isEnabled(flag, userId);
    }

    /**
     * 获取全部特性开关的当前快照
     *
     * @return 特性开关快照列表
     */
    @Override
    public List<FeatureFlagSnapshot> snapshot() {
        return featureFlagService.snapshot();
    }

    /**
     * 按分类分组获取特性开关快照
     *
     * @return key=分类，value=该分类下的特性开关快照列表
     */
    @Override
    public Map<String, List<FeatureFlagSnapshot>> snapshotByCategory() {
        return featureFlagService.snapshotByCategory();
    }

    /**
     * 设置特性开关的启用/禁用状态
     *
     * @param flag    特性开关枚举
     * @param enabled 是否启用
     * @return true 表示设置成功
     */
    @Override
    public boolean setEnabled(FeatureFlag flag, boolean enabled) {
        return featureFlagService.setEnabled(flag, enabled);
    }

    /**
     * 设置特性开关的灰度发布百分比
     *
     * @param flag       特性开关枚举
     * @param percentage 灰度百分比（0-100）
     * @return 影响的记录数
     */
    @Override
    public int setRolloutPercentage(FeatureFlag flag, int percentage) {
        return featureFlagService.setRolloutPercentage(flag, percentage);
    }

    /**
     * 刷新特性开关缓存（从数据源重新加载）
     */
    @Override
    public void refresh() {
        featureFlagService.refresh();
    }
}