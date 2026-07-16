package com.njydsz.system.server.service.impl.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.njydsz.common.core.featureflag.FeatureFlag;
import com.njydsz.common.core.featureflag.FeatureFlagService;
import com.njydsz.common.core.featureflag.FeatureFlagSnapshot;

import lombok.extern.slf4j.Slf4j;

/**
 * 特性开关服务实现（内存版）
 *
 * <p>基于 {@link ConcurrentHashMap} 的进程内实现，进程重启后状态丢失。
 * 适用于开发阶段；后续可替换为数据库 / 配置中心实现。
 *
 * <p>灰度发布规则：当配置了 {@code rolloutPercentage}（0-100）时，按用户 ID 哈希取模
 * 判断是否命中灰度；未传 userId 时仅在 100% 时返回 true。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class FeatureFlagServiceImpl implements FeatureFlagService {

    /** 单个开关的运行时状态 */
    private static final class FlagState {
        Boolean configuredValue;
        Integer rolloutPercentage;
        LocalDateTime updatedAt;

        FlagState(Boolean configuredValue, Integer rolloutPercentage, LocalDateTime updatedAt) {
            this.configuredValue = configuredValue;
            this.rolloutPercentage = rolloutPercentage;
            this.updatedAt = updatedAt;
        }
    }

    /** 运行时状态表 */
    private final ConcurrentHashMap<FeatureFlag, FlagState> states = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled(FeatureFlag flag, String userId) {
        if (flag.isMandatory()) {
            return true;
        }
        FlagState state = states.get(flag);
        if (state == null || state.configuredValue == null) {
            return false;
        }
        if (!state.configuredValue) {
            return false;
        }
        Integer rollout = state.rolloutPercentage;
        if (rollout == null || rollout >= 100) {
            return true;
        }
        if (rollout <= 0) {
            return false;
        }
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        return Math.floorMod(userId.hashCode(), 100) < rollout;
    }

    @Override
    public List<FeatureFlagSnapshot> snapshot() {
        LocalDateTime now = LocalDateTime.now();
        return Arrays.stream(FeatureFlag.values())
                .map(flag -> toSnapshot(flag, states.get(flag), now))
                .toList();
    }

    @Override
    public Map<String, List<FeatureFlagSnapshot>> snapshotByCategory() {
        Map<String, List<FeatureFlagSnapshot>> grouped = new LinkedHashMap<>();
        for (FeatureFlagSnapshot s : snapshot()) {
            grouped.computeIfAbsent(s.getCategory().name(), k -> new ArrayList<>()).add(s);
        }
        return grouped;
    }

    @Override
    public boolean setEnabled(FeatureFlag flag, boolean enabled) {
        if (flag.isMandatory()) {
            log.info("[FeatureFlag] {} 为强制开启，忽略禁用操作", flag.name());
            return true;
        }
        FlagState state = states.compute(flag, (k, v) -> {
            LocalDateTime now = LocalDateTime.now();
            Boolean newVal = enabled;
            Integer rollout = v == null ? null : v.rolloutPercentage;
            return new FlagState(newVal, rollout, now);
        });
        log.info("[FeatureFlag] {} 已设置为 {}", flag.name(), enabled);
        return isEnabled(flag, null);
    }

    @Override
    public int setRolloutPercentage(FeatureFlag flag, int percentage) {
        int clamped = Math.max(0, Math.min(100, percentage));
        states.compute(flag, (k, v) -> {
            LocalDateTime now = LocalDateTime.now();
            Boolean configured = v == null ? null : v.configuredValue;
            if (configured == null) {
                configured = clamped > 0;
            }
            return new FlagState(configured, clamped, now);
        });
        log.info("[FeatureFlag] {} 灰度比例已设置为 {}%", flag.name(), clamped);
        return clamped;
    }

    @Override
    public void refresh() {
        states.clear();
        log.info("[FeatureFlag] 内存状态已清空，所有开关回到默认值");
    }

    /**
     * 将枚举与运行时状态组装为快照
     *
     * @param flag  开关枚举
     * @param state 运行时状态（可为 null）
     * @param now   当前时间（未配置时使用）
     * @return 快照
     */
    private FeatureFlagSnapshot toSnapshot(FeatureFlag flag, FlagState state, LocalDateTime now) {
        boolean mandatory = flag.isMandatory();
        Boolean configured = mandatory ? Boolean.TRUE : (state == null ? null : state.configuredValue);
        boolean effective = mandatory || (configured != null && configured && isEnabled(flag, null));
        Integer rollout = state == null ? null : state.rolloutPercentage;
        LocalDateTime updatedAt = state == null ? now : state.updatedAt;
        return FeatureFlagSnapshot.builder()
                .key(flag.name())
                .category(flag.getCategory())
                .description(flag.getDescription())
                .configuredValue(mandatory ? Boolean.TRUE : configured)
                .effectiveValue(effective)
                .mandatory(mandatory)
                .rolloutPercentage(rollout)
                .updatedAt(updatedAt)
                .build();
    }
}
