package com.njydsz.pmis.common.featureflag;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.feign.ConfigClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FeatureFlag 单元测试
 *
 * <p>覆盖以下场景:
 * <ul>
 *   <li>SAFETY 类永远开启</li>
 *   <li>默认行为遵循 {@link FeatureFlag#isEnabledByDefault()}</li>
 *   <li>config 中心可覆盖</li>
 *   <li>灰度发布按 userId 哈希分桶</li>
 *   <li>Feign 异常降级到本地缓存</li>
 *   <li>刷新缓存立即生效</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FeatureFlag 特性开关")
class FeatureFlagTest {

    private ConfigClient configClient;
    private LocalFeatureFlagService service;

    @BeforeEach
    void setUp() {
        configClient = mock(ConfigClient.class);
        service = new LocalFeatureFlagService();
        service.setConfigClientForTest(configClient);
    }

    @Test
    @DisplayName("SAFETY 类 flag 永远返回 true")
    void safetyFlagsAlwaysEnabled() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AUDIT_LOG_MANDATORY.configKey(), "false",
                        FeatureFlag.SENSITIVE_REAUTH.configKey(), "false"
                )));
        assertThat(service.isEnabled(FeatureFlag.AUDIT_LOG_MANDATORY)).isTrue();
        assertThat(service.isEnabled(FeatureFlag.SENSITIVE_REAUTH)).isTrue();
        assertThat(service.isEnabled(FeatureFlag.DATA_EXPORT_AUDIT)).isTrue();
        assertThat(service.isEnabled(FeatureFlag.TOTP_TWO_FACTOR)).isTrue();
    }

    @Test
    @DisplayName("未配置时, 业务类 flag 默认关闭")
    void businessFlagsDefaultDisabled() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of()));
        assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION)).isFalse();
        assertThat(service.isEnabled(FeatureFlag.ADVANCED_PROFIT_SIMULATION)).isFalse();
        assertThat(service.isEnabled(FeatureFlag.COCKPIT_V2)).isFalse();
    }

    @Test
    @DisplayName("config 显式 true 后, flag 启用")
    void configOverrideTrue() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                        FeatureFlag.COCKPIT_V2.configKey(), "1"
                )));
        assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION)).isTrue();
        assertThat(service.isEnabled(FeatureFlag.COCKPIT_V2)).isTrue();
    }

    @Test
    @DisplayName("config 显式 false 后, flag 关闭")
    void configOverrideFalse() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(FeatureFlag.I18N_LOCALIZATION.configKey(), "false")));
        // I18N 业务默认 false, 再显式 false → 仍 false
        assertThat(service.isEnabled(FeatureFlag.I18N_LOCALIZATION)).isFalse();
    }

    @Test
    @DisplayName("灰度发布 50% 时, 一部分 userId 命中, 一部分未命中")
    void rolloutHalf() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                        FeatureFlag.AGENT_ORCHESTRATION.configKey() + ".rollout", "50"
                )));
        // 统计 1000 个用户的命中比例
        int hit = 0;
        int total = 1000;
        for (long uid = 1; uid <= total; uid++) {
            if (service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION, uid)) hit++;
        }
        // 期望 ~ 50%, 留 ±10% 误差 (实际接近 50%)
        assertThat(hit).isBetween(400, 600);
    }

    @Test
    @DisplayName("灰度发布 0% 时, 任何用户都不命中")
    void rolloutZero() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                        FeatureFlag.AGENT_ORCHESTRATION.configKey() + ".rollout", "0"
                )));
        for (long uid = 1; uid <= 100; uid++) {
            assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION, uid)).isFalse();
        }
    }

    @Test
    @DisplayName("灰度发布 100% 时, 任何用户都命中")
    void rolloutFull() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                        FeatureFlag.AGENT_ORCHESTRATION.configKey() + ".rollout", "100"
                )));
        for (long uid = 1; uid <= 100; uid++) {
            assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION, uid)).isTrue();
        }
    }

    @Test
    @DisplayName("灰度发布但 userId=null 时, 视为不在白名单")
    void rolloutWithNullUserId() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                        FeatureFlag.AGENT_ORCHESTRATION.configKey() + ".rollout", "100"
                )));
        // userId=null + rollout<100 → false
        // 验证: rollout=50 + null user → false
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                        FeatureFlag.AGENT_ORCHESTRATION.configKey() + ".rollout", "50"
                )));
        service.refresh();
        assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION, null)).isFalse();
    }

    @Test
    @DisplayName("同一 userId 多次调用结果一致 (粘性)")
    void rolloutSticky() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                        FeatureFlag.AGENT_ORCHESTRATION.configKey() + ".rollout", "30"
                )));
        boolean first = service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION, 12345L);
        for (int i = 0; i < 10; i++) {
            assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION, 12345L)).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("Feign 异常时, 使用本地 testStore 降级")
    void feignFailureFallback() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenThrow(new RuntimeException("nacos down"));
        service.primeTestStore(Map.of(FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true"));
        // 必须返回 true (来自 testStore)
        assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION)).isTrue();
    }

    @Test
    @DisplayName("snapshot 包含所有 flag 的当前状态")
    void snapshotComplete() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of(
                        FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                        FeatureFlag.COCKPIT_V2.configKey(), "true",
                        FeatureFlag.COCKPIT_V2.configKey() + ".rollout", "25"
                )));
        List<FeatureFlagSnapshot> snap = service.snapshot();
        assertThat(snap).hasSize(FeatureFlag.values().length);
        FeatureFlagSnapshot agentSnap = snap.stream()
                .filter(s -> s.getKey().equals(FeatureFlag.AGENT_ORCHESTRATION.name()))
                .findFirst().orElseThrow();
        assertThat(agentSnap.isEffectiveValue()).isTrue();
        assertThat(agentSnap.getConfiguredValue()).isTrue();
        assertThat(agentSnap.isMandatory()).isFalse();

        FeatureFlagSnapshot cockpitSnap = snap.stream()
                .filter(s -> s.getKey().equals(FeatureFlag.COCKPIT_V2.name()))
                .findFirst().orElseThrow();
        assertThat(cockpitSnap.getRolloutPercentage()).isEqualTo(25);
    }

    @Test
    @DisplayName("snapshotByCategory 按 4 个分类聚合")
    void snapshotGrouped() {
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of()));
        Map<String, List<FeatureFlagSnapshot>> grouped = service.snapshotByCategory();
        assertThat(grouped).containsKeys("INFRASTRUCTURE", "BUSINESS", "UI", "SAFETY");
        // SAFETY 至少 4 个
        assertThat(grouped.get("SAFETY")).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("setEnabled 后立即生效 (refresh 一次缓存)")
    void setEnabledImmediate() {
        // 首次: 未配置 → 默认 false
        when(configClient.getGroup(eq(FeatureFlagService.CONFIG_GROUP)))
                .thenReturn(Result.ok(Map.of()));
        assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION)).isFalse();
        // 写入本地 store
        service.setEnabled(FeatureFlag.AGENT_ORCHESTRATION, true);
        // 由于 setEnabled 写本地 + 失效缓存, 重新读取走 testStore
        assertThat(service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION)).isTrue();
    }

    @Test
    @DisplayName("SAFETY 类 flag setEnabled(false) 实际保持 true")
    void safetyFlagCantBeDisabled() {
        boolean R = service.setEnabled(FeatureFlag.AUDIT_LOG_MANDATORY, false);
        assertThat(R).isTrue();
        // 再次读取必须仍为 true
        assertThat(service.isEnabled(FeatureFlag.AUDIT_LOG_MANDATORY)).isTrue();
    }

    @Test
    @DisplayName("setRolloutPercentage 自动 clamp 到 0-100")
    void setRolloutClamp() {
        assertThat(service.setRolloutPercentage(FeatureFlag.COCKPIT_V2, 150)).isEqualTo(100);
        assertThat(service.setRolloutPercentage(FeatureFlag.COCKPIT_V2, -10)).isEqualTo(0);
        assertThat(service.setRolloutPercentage(FeatureFlag.COCKPIT_V2, 50)).isEqualTo(50);
    }

    @Test
    @DisplayName("灰度用户数接近配置百分比 (200 用户 / 20%)")
    void rolloutRoughlyAccurate() {
        service.primeTestStore(Map.of(
                FeatureFlag.AGENT_ORCHESTRATION.configKey(), "true",
                FeatureFlag.AGENT_ORCHESTRATION.configKey() + ".rollout", "20"
        ));
        int hit = 0;
        int total = 200;
        for (long uid = 1; uid <= total; uid++) {
            if (service.isEnabled(FeatureFlag.AGENT_ORCHESTRATION, uid)) hit++;
        }
        // 20% × 200 = 40, 留 ±15 容差 (哈希分桶)
        assertThat(hit).isBetween(25, 55);
    }

    @Test
    @DisplayName("isUserInRollout 工具方法直接测试")
    void rolloutHelperDirect() {
        // rollout=0 → 任何人 false
        for (long uid = 0; uid < 1000; uid++) {
            assertThat(LocalFeatureFlagService.isUserInRollout(uid, 0)).isFalse();
        }
        // rollout=100 → 任何人 true
        for (long uid = 0; uid < 1000; uid++) {
            assertThat(LocalFeatureFlagService.isUserInRollout(uid, 100)).isTrue();
        }
        // rollout=10: 1000 用户中应当命中 ~100
        int hit = 0;
        for (long uid = 0; uid < 1000; uid++) {
            if (LocalFeatureFlagService.isUserInRollout(uid, 10)) hit++;
        }
        assertThat(hit).isBetween(50, 150);
    }
}
