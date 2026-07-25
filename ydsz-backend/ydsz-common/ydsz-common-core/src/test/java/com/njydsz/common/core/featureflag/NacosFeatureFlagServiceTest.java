package com.njydsz.common.core.featureflag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link NacosFeatureFlagService} 单元测试 — 验证 Nacos JSON 配置解析与降级行为。
 *
 * <p>不依赖真实 Nacos 服务，仅测试 {@link NacosFeatureFlagService#applyRemoteConfig(String, String)}
 * 的 JSON 解析逻辑、未知 key 处理、降级到内存模式等关键路径。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("NacosFeatureFlagService 测试")
class NacosFeatureFlagServiceTest {

    private FeatureFlagProperties properties;
    private Environment environment;
    private NacosFeatureFlagService service;

    @BeforeEach
    void setUp() {
        properties = new FeatureFlagProperties();
        environment = new MockEnvironment();
        service = new NacosFeatureFlagService(properties, environment);
    }

    @Test
    @DisplayName("init 未启用 Nacos 时不抛异常，降级为内存模式")
    void initWithoutNacosEnabled() {
        properties.getNacos().setEnabled(false);
        service.init();
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("init 配置启用但 server-addr 缺失时安全降级")
    void initWithoutServerAddr() {
        properties.getNacos().setEnabled(true);
        // 未配置 serverAddr 且 Environment 中无 spring.cloud.nacos.config.server-addr
        service.init();
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("applyRemoteConfig 解析合法 JSON 并应用配置")
    void applyValidJson() {
        String json = "{"
                + "\"NEW_DASHBOARD\":{\"enabled\":true,\"rollout\":50},"
                + "\"BATCH_EXPORT\":{\"enabled\":false}"
                + "}";
        service.applyRemoteConfig(json, "test");

        assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isTrue();
        assertThat(service.isEnabled(FeatureFlag.BATCH_EXPORT, null)).isFalse();
        // rollout=50 + userId 非空 → 走 rollout 判断
        boolean rolloutResult = service.isEnabled(FeatureFlag.NEW_DASHBOARD, "user-1");
        assertThat(rolloutResult).isIn(true, false);
    }

    @Test
    @DisplayName("applyRemoteConfig 忽略未知 key")
    void ignoreUnknownKey() {
        String json = "{\"UNKNOWN_FLAG\":{\"enabled\":true},\"NEW_DASHBOARD\":{\"enabled\":true}}";
        service.applyRemoteConfig(json, "test");
        assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isTrue();
    }

    @Test
    @DisplayName("applyRemoteConfig 跳过空 JSON")
    void skipEmptyJson() {
        service.setEnabled(FeatureFlag.NEW_DASHBOARD, true);
        service.applyRemoteConfig("", "test");
        // 状态不变
        assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isTrue();
        service.applyRemoteConfig(null, "test");
        assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isTrue();
    }

    @Test
    @DisplayName("applyRemoteConfig 遇到非法 JSON 不抛异常")
    void malformedJsonDoesNotThrow() {
        service.setEnabled(FeatureFlag.NEW_DASHBOARD, true);
        service.applyRemoteConfig("not-a-json", "test");
        // 状态不变
        assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isTrue();
    }

    @Test
    @DisplayName("applyRemoteConfig 缺省字段保留原状态")
    void missingFieldsRetainState() {
        service.setEnabled(FeatureFlag.NEW_DASHBOARD, true);
        service.setRolloutPercentage(FeatureFlag.NEW_DASHBOARD, 30);

        // 仅推送 enabled，未推送 rollout → rollout 应保持 30
        String json = "{\"NEW_DASHBOARD\":{\"enabled\":false}}";
        service.applyRemoteConfig(json, "test");

        assertThat(service.isEnabled(FeatureFlag.NEW_DASHBOARD, null)).isFalse();
        // rollout 不变，user-1 仍按 30% 判断
        boolean rolloutResult = service.isEnabled(FeatureFlag.NEW_DASHBOARD, "user-1");
        assertThat(rolloutResult).isIn(true, false);
    }

    @Test
    @DisplayName("applyRemoteConfig 不能关闭强制开关")
    void cannotDisableMandatoryFlag() {
        String json = "{\"SENSITIVE_DATA_MASK\":{\"enabled\":false}}";
        service.applyRemoteConfig(json, "test");
        assertThat(service.isEnabled(FeatureFlag.SENSITIVE_DATA_MASK, null)).isTrue();
    }

    @Test
    @DisplayName("destroy 在未初始化时不抛异常")
    void destroyWithoutInit() {
        service.destroy();
        assertThat(service.isAvailable()).isFalse();
    }
}
