package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.ConfigClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ThresholdProvider 单元测试
 *
 * <p>覆盖默认值、配置覆盖、格式错误回退与远端异常兜底场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ThresholdProvider 阈值提供器")
class ThresholdProviderTest {

    private ConfigClient configClient;
    private ThresholdProvider provider;

    @BeforeEach
    void setUp() {
        configClient = mock(ConfigClient.class);
        provider = new ThresholdProvider(configClient);
    }

    @Test
    @DisplayName("未配置时使用默认值")
    void defaultWhenNoConfig() {
        when(configClient.getGroup(eq(ThresholdProvider.GROUP)))
                .thenReturn(Result.ok(Map.of()));
        assertThat(provider.cpiYellow()).isEqualTo(0.95);
        assertThat(provider.cpiRed()).isEqualTo(0.85);
        assertThat(provider.spiYellow()).isEqualTo(0.90);
        assertThat(provider.spiRed()).isEqualTo(0.80);
        assertThat(provider.benchYellowDays()).isEqualTo(7);
        assertThat(provider.benchRedDays()).isEqualTo(15);
    }

    @Test
    @DisplayName("配置覆盖时使用配置值")
    void overrideFromConfig() {
        when(configClient.getGroup(eq(ThresholdProvider.GROUP)))
                .thenReturn(Result.ok(Map.of(
                        "alert.cpi.yellow", "0.97",
                        "alert.cpi.red", "0.90",
                        "alert.bench.days.yellow", "5",
                        "alert.bench.days.red", "10"
                )));
        assertThat(provider.cpiYellow()).isEqualTo(0.97);
        assertThat(provider.cpiRed()).isEqualTo(0.90);
        assertThat(provider.benchYellowDays()).isEqualTo(5);
        assertThat(provider.benchRedDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("格式错误时回退默认")
    void fallbackOnInvalidValue() {
        when(configClient.getGroup(eq(ThresholdProvider.GROUP)))
                .thenReturn(Result.ok(Map.of("alert.cpi.yellow", "not-a-number")));
        assertThat(provider.cpiYellow()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Feign 异常时返回上次缓存或默认值")
    void fallbackOnRemoteFail() {
        when(configClient.getGroup(eq(ThresholdProvider.GROUP)))
                .thenThrow(new RuntimeException("nacos down"));
        // 第一次调用 → 拿不到值，缓存空 → 返回默认
        assertThat(provider.cpiYellow()).isEqualTo(0.95);
    }
}
