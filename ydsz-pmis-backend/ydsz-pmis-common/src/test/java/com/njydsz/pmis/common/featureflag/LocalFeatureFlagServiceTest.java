package com.njydsz.pmis.common.featureflag;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.ConfigClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LocalFeatureFlagService 特性开关服务单元测试
 *
 * <p>覆盖 isEnabled / snapshot / snapshotByCategory / setEnabled / setRolloutPercentage
 * / refresh 方法, 包括 mandatory 强制开启、灰度发布、ConfigClient 降级等场景.
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalFeatureFlagService 特性开关服务测试")
class LocalFeatureFlagServiceTest {

    @Mock
    private ObjectProvider<ConfigClient> configClientProvider;

    private LocalFeatureFlagService featureFlagService;

    @BeforeEach
    void setUp() {
        // ObjectProvider 返回 null → configClient=null → 使用 testStore 作为配置源
        when(configClientProvider.getIfAvailable()).thenReturn(null);
        featureFlagService = new LocalFeatureFlagService(configClientProvider);
    }

    // ==================== isEnabled ====================

    @Test
    @DisplayName("正常场景：SAFETY 类 flag 强制开启，不受 config 影响")
    void isEnabled_mandatory强制开启() {
        // 即使 config 中设置为 false，mandatory flag 仍然返回 true
        featureFlagService.setTestValue(FeatureFlag.AUDIT_LOG_MANDATORY.configKey(), "false");

        assertTrue(featureFlagService.isEnabled(FeatureFlag.AUDIT_LOG_MANDATORY));
        assertTrue(featureFlagService.isEnabled(FeatureFlag.AUDIT_LOG_MANDATORY, "user1"));
    }

    @Test
    @DisplayName("正常场景：非 mandatory flag 未配置时使用默认值")
    void isEnabled_未配置_使用默认值() {
        // 非 SAFETY 类默认 false
        assertFalse(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2));
        assertFalse(featureFlagService.isEnabled(FeatureFlag.AGENT_ORCHESTRATION, "user1"));
    }

    @Test
    @DisplayName("正常场景：config 中显式设置为 true")
    void isEnabled_config设置为True() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");

        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2));
    }

    @Test
    @DisplayName("正常场景：config 中显式设置为 false")
    void isEnabled_config设置为False() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "false");

        assertFalse(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2));
    }

    @Test
    @DisplayName("正常场景：config 值为 1 视为 true")
    void isEnabled_config值为1() {
        featureFlagService.setTestValue(FeatureFlag.DARK_MODE.configKey(), "1");

        assertTrue(featureFlagService.isEnabled(FeatureFlag.DARK_MODE));
    }

    @Test
    @DisplayName("正常场景：config 值为 yes 视为 true")
    void isEnabled_config值为Yes() {
        featureFlagService.setTestValue(FeatureFlag.DARK_MODE.configKey(), "yes");

        assertTrue(featureFlagService.isEnabled(FeatureFlag.DARK_MODE));
    }

    @Test
    @DisplayName("正常场景：enabled 但无 rollout 视为全量开启")
    void isEnabled_无rollout_全量() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");

        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, "user1"));
    }

    @Test
    @DisplayName("正常场景：rollout=100 全量开启")
    void isEnabled_rollout100_全量() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey() + ".rollout", "100");

        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, "user1"));
    }

    @Test
    @DisplayName("正常场景：rollout=0 全量关闭")
    void isEnabled_rollout0_全量关闭() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey() + ".rollout", "0");

        assertFalse(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, "user1"));
    }

    @Test
    @DisplayName("正常场景：enabled 但 rollout 设置且 userId 为 null 返回 false")
    void isEnabled_rollout设置_userId为Null() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey() + ".rollout", "50");

        assertFalse(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, null));
    }

    @Test
    @DisplayName("正常场景：rollout=50 且 userId 命中灰度白名单返回 true")
    void isEnabled_rollout50_命中灰度() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey() + ".rollout", "50");

        // 选择一个 hashCode mod 100 < 50 的 userId
        String userId = findUserIdInRollout(50);
        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, userId));
    }

    @Test
    @DisplayName("正常场景：rollout=50 且 userId 未命中灰度白名单返回 false")
    void isEnabled_rollout50_未命中灰度() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey() + ".rollout", "50");

        // 选择一个 hashCode mod 100 >= 50 的 userId
        String userId = findUserIdOutOfRollout(50);
        assertFalse(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, userId));
    }

    // ==================== isUserInRollout (静态方法) ====================

    @Test
    @DisplayName("正常场景：rollout=100 所有用户命中")
    void isUserInRollout_100_全部命中() {
        assertTrue(LocalFeatureFlagService.isUserInRollout("anyUser", 100));
        assertTrue(LocalFeatureFlagService.isUserInRollout("otherUser", 100));
    }

    @Test
    @DisplayName("正常场景：rollout=0 无用户命中")
    void isUserInRollout_0_无命中() {
        assertFalse(LocalFeatureFlagService.isUserInRollout("anyUser", 0));
    }

    @Test
    @DisplayName("正常场景：同一用户多次调用结果一致（粘性）")
    void isUserInRollout_粘性一致() {
        String userId = "sticky-user-123";
        boolean first = LocalFeatureFlagService.isUserInRollout(userId, 50);
        boolean second = LocalFeatureFlagService.isUserInRollout(userId, 50);

        assertEquals(first, second);
    }

    // ==================== snapshot ====================

    @Test
    @DisplayName("正常场景：snapshot 返回所有 flag 的快照")
    void snapshot_返回全部() {
        List<FeatureFlagSnapshot> snapshots = featureFlagService.snapshot();

        assertEquals(FeatureFlag.values().length, snapshots.size());
    }

    @Test
    @DisplayName("正常场景：snapshot 中 SAFETY 类 flag effectiveValue=true")
    void snapshot_safety类强制开启() {
        List<FeatureFlagSnapshot> snapshots = featureFlagService.snapshot();

        FeatureFlagSnapshot auditSnapshot = snapshots.stream()
                .filter(s -> FeatureFlag.AUDIT_LOG_MANDATORY.name().equals(s.getKey()))
                .findFirst().orElse(null);

        assertNotNull(auditSnapshot);
        assertTrue(auditSnapshot.isEffectiveValue());
        assertTrue(auditSnapshot.isMandatory());
        assertTrue(auditSnapshot.getConfiguredValue() == null);
    }

    @Test
    @DisplayName("正常场景：snapshot 中配置过的 flag 反映配置值")
    void snapshot_配置值正确() {
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey() + ".rollout", "30");

        List<FeatureFlagSnapshot> snapshots = featureFlagService.snapshot();

        FeatureFlagSnapshot cockpitSnapshot = snapshots.stream()
                .filter(s -> FeatureFlag.COCKPIT_V2.name().equals(s.getKey()))
                .findFirst().orElse(null);

        assertNotNull(cockpitSnapshot);
        assertEquals(Boolean.TRUE, cockpitSnapshot.getConfiguredValue());
        assertTrue(cockpitSnapshot.isEffectiveValue());
        assertEquals(30, cockpitSnapshot.getRolloutPercentage());
    }

    // ==================== snapshotByCategory ====================

    @Test
    @DisplayName("正常场景：snapshotByCategory 按分类分组")
    void snapshotByCategory_按分类分组() {
        Map<String, List<FeatureFlagSnapshot>> grouped = featureFlagService.snapshotByCategory();

        assertNotNull(grouped.get("INFRASTRUCTURE"));
        assertNotNull(grouped.get("BUSINESS"));
        assertNotNull(grouped.get("UI"));
        assertNotNull(grouped.get("SAFETY"));

        int total = grouped.values().stream().mapToInt(List::size).sum();
        assertEquals(FeatureFlag.values().length, total);
    }

    // ==================== setEnabled ====================

    @Test
    @DisplayName("正常场景：setEnabled 对非 mandatory flag 写入配置")
    void setEnabled_非mandatory() {
        boolean result = featureFlagService.setEnabled(FeatureFlag.COCKPIT_V2, true);

        assertTrue(result);
        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2));
    }

    @Test
    @DisplayName("正常场景：setEnabled 对 mandatory flag 始终返回 true 不写入")
    void setEnabled_mandatory() {
        boolean result = featureFlagService.setEnabled(FeatureFlag.AUDIT_LOG_MANDATORY, false);

        assertTrue(result);
        // SAFETY 类仍然开启
        assertTrue(featureFlagService.isEnabled(FeatureFlag.AUDIT_LOG_MANDATORY));
    }

    // ==================== setRolloutPercentage ====================

    @Test
    @DisplayName("正常场景：setRolloutPercentage 正常值写入")
    void setRolloutPercentage_正常值() {
        int result = featureFlagService.setRolloutPercentage(FeatureFlag.COCKPIT_V2, 50);

        assertEquals(50, result);
    }

    @Test
    @DisplayName("边界场景：setRolloutPercentage 超过 100 被 clamp 到 100")
    void setRolloutPercentage_超过100() {
        int result = featureFlagService.setRolloutPercentage(FeatureFlag.COCKPIT_V2, 150);

        assertEquals(100, result);
    }

    @Test
    @DisplayName("边界场景：setRolloutPercentage 小于 0 被 clamp 到 0")
    void setRolloutPercentage_小于0() {
        int result = featureFlagService.setRolloutPercentage(FeatureFlag.COCKPIT_V2, -10);

        assertEquals(0, result);
    }

    // ==================== refresh ====================

    @Test
    @DisplayName("正常场景：refresh 清空缓存后重新读取配置")
    void refresh_清空缓存() {
        // 第一次读取
        assertFalse(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2));

        // 写入新值并刷新
        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.refresh();

        // 刷新后读到新值
        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2));
    }

    // ==================== ConfigClient 集成 ====================

    @Test
    @DisplayName("正常场景：ConfigClient 可用时从远程拉取配置")
    void isEnabled_configClient可用() {
        ConfigClient mockClient = mock(ConfigClient.class);
        Map<String, String> remote = new HashMap<>();
        remote.put(FeatureFlag.COCKPIT_V2.configKey(), "true");
        when(mockClient.getGroup(FeatureFlagService.CONFIG_GROUP)).thenReturn(Result.ok(remote));

        featureFlagService.setConfigClientForTest(mockClient);
        featureFlagService.refresh();

        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, "user1"));
    }

    @Test
    @DisplayName("异常场景：ConfigClient 抛异常降级到本地 testStore")
    void isEnabled_configClient异常_降级() {
        ConfigClient mockClient = mock(ConfigClient.class);
        when(mockClient.getGroup(FeatureFlagService.CONFIG_GROUP)).thenThrow(new RuntimeException("connect refused"));

        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.setConfigClientForTest(mockClient);
        featureFlagService.refresh();

        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, "user1"));
    }

    @Test
    @DisplayName("边界场景：ConfigClient 返回 null 降级到本地 testStore")
    void isEnabled_configClient返回Null_降级() {
        ConfigClient mockClient = mock(ConfigClient.class);
        when(mockClient.getGroup(FeatureFlagService.CONFIG_GROUP)).thenReturn(null);

        featureFlagService.setTestValue(FeatureFlag.COCKPIT_V2.configKey(), "true");
        featureFlagService.setConfigClientForTest(mockClient);
        featureFlagService.refresh();

        assertTrue(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, "user1"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找一个 hashCode mod 100 < rolloutPercentage 的 userId
     */
    private String findUserIdInRollout(int rolloutPercentage) {
        for (int i = 0; i < 1000; i++) {
            String uid = "user-" + i;
            if (LocalFeatureFlagService.isUserInRollout(uid, rolloutPercentage)) {
                return uid;
            }
        }
        throw new IllegalStateException("找不到命中灰度的 userId");
    }

    /**
     * 查找一个 hashCode mod 100 >= rolloutPercentage 的 userId
     */
    private String findUserIdOutOfRollout(int rolloutPercentage) {
        for (int i = 0; i < 1000; i++) {
            String uid = "user-" + i;
            if (!LocalFeatureFlagService.isUserInRollout(uid, rolloutPercentage)) {
                return uid;
            }
        }
        throw new IllegalStateException("找不到未命中灰度的 userId");
    }
}
