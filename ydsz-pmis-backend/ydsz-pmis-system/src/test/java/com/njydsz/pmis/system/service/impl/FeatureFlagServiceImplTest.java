package com.njydsz.pmis.system.service.impl;

import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import com.njydsz.pmis.common.featureflag.FeatureFlagSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureFlagServiceImpl 单元测试")
class FeatureFlagServiceImplTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private FeatureFlagServiceImpl featureFlagServiceImpl;

    @Nested
    @DisplayName("isEnabled 方法")
    class IsEnabledTest {

        @Test
        @DisplayName("特性开关启用时应返回 true")
        void shouldReturnTrueWhenEnabled() {
            when(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, null)).thenReturn(true);

            boolean result = featureFlagServiceImpl.isEnabled(FeatureFlag.COCKPIT_V2, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("特性开关禁用时应返回 false")
        void shouldReturnFalseWhenDisabled() {
            when(featureFlagService.isEnabled(FeatureFlag.DARK_MODE, null)).thenReturn(false);

            boolean result = featureFlagServiceImpl.isEnabled(FeatureFlag.DARK_MODE, null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("带用户维度的灰度判断应正确委托")
        void shouldDelegateToFeatureFlagService() {
            when(featureFlagService.isEnabled(FeatureFlag.COCKPIT_V2, 123L)).thenReturn(true);

            boolean result = featureFlagServiceImpl.isEnabled(FeatureFlag.COCKPIT_V2, 123L);

            assertThat(result).isTrue();
            verify(featureFlagService).isEnabled(FeatureFlag.COCKPIT_V2, 123L);
        }
    }

    @Nested
    @DisplayName("setEnabled 方法")
    class SetEnabledTest {

        @Test
        @DisplayName("设置开关状态应正确委托")
        void shouldDelegateSetEnabled() {
            when(featureFlagService.setEnabled(FeatureFlag.COCKPIT_V2, true)).thenReturn(true);

            boolean result = featureFlagServiceImpl.setEnabled(FeatureFlag.COCKPIT_V2, true);

            assertThat(result).isTrue();
            verify(featureFlagService).setEnabled(FeatureFlag.COCKPIT_V2, true);
        }
    }

    @Nested
    @DisplayName("snapshot 方法")
    class SnapshotTest {

        @Test
        @DisplayName("获取快照应返回正确结果")
        void shouldReturnSnapshot() {
            FeatureFlagSnapshot snap = FeatureFlagSnapshot.builder()
                    .key("COCKPIT_V2")
                    .category("UI")
                    .effectiveValue(true)
                    .build();
            when(featureFlagService.snapshot()).thenReturn(List.of(snap));

            List<FeatureFlagSnapshot> result = featureFlagServiceImpl.snapshot();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getKey()).isEqualTo("COCKPIT_V2");
        }

        @Test
        @DisplayName("无快照时应返回空列表")
        void shouldReturnEmptyWhenNoSnapshot() {
            when(featureFlagService.snapshot()).thenReturn(Collections.emptyList());

            List<FeatureFlagSnapshot> result = featureFlagServiceImpl.snapshot();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("refresh 方法")
    class RefreshTest {

        @Test
        @DisplayName("刷新缓存应正确委托")
        void shouldDelegateRefresh() {
            assertThatCode(() -> featureFlagServiceImpl.refresh()).doesNotThrowAnyException();
            verify(featureFlagService).refresh();
        }
    }
}