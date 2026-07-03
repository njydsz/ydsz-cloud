package com.njydsz.pmis.system.service.impl;

import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import com.njydsz.pmis.common.featureflag.FeatureFlagSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    @Override
    public boolean isEnabled(FeatureFlag flag, Long userId) {
        return featureFlagService.isEnabled(flag, userId);
    }

    @Override
    public List<FeatureFlagSnapshot> snapshot() {
        return featureFlagService.snapshot();
    }

    @Override
    public Map<String, List<FeatureFlagSnapshot>> snapshotByCategory() {
        return featureFlagService.snapshotByCategory();
    }

    @Override
    public boolean setEnabled(FeatureFlag flag, boolean enabled) {
        return featureFlagService.setEnabled(flag, enabled);
    }

    @Override
    public int setRolloutPercentage(FeatureFlag flag, int percentage) {
        return featureFlagService.setRolloutPercentage(flag, percentage);
    }

    @Override
    public void refresh() {
        featureFlagService.refresh();
    }
}