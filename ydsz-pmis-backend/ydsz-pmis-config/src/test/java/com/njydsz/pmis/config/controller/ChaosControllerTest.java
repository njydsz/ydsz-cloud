package com.njydsz.pmis.config.controller;

import com.njydsz.pmis.common.chaos.ChaosExperiment;
import com.njydsz.pmis.common.chaos.ChaosOutcome;
import com.njydsz.pmis.common.chaos.ChaosService;
import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChaosController 单元测试 (批次 20 P3-3)
 *
 * <p>绕过 @PrePermission 切面, 直接通过反射调用 controller 方法, 验证业务行为.
 */
@DisplayName("ChaosController 混沌工程接口")
class ChaosControllerTest {

    private FeatureFlagService featureFlagService;
    private ChaosService chaosService;
    private ChaosController controller;

    @BeforeEach
    void setUp() {
        featureFlagService = mock(FeatureFlagService.class);
        chaosService = new ChaosService(featureFlagService);
        controller = new ChaosController(chaosService);
    }

    @Test
    @DisplayName("list 返回空列表")
    void list_empty() {
        assertThat(controller.list().getData()).isEmpty();
    }

    @Test
    @DisplayName("register + list 能看到刚注册的实验")
    void register_then_list() {
        controller.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_LATENCY)
                .latencyMs(100L)
                .enabled(true)
                .createdBy("tester")
                .build());
        List<ChaosExperiment> all = controller.list().getData();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getTarget()).isEqualTo("X.y");
    }

    @Test
    @DisplayName("get 按 target 查询")
    void getByTarget() {
        controller.register(ChaosExperiment.builder()
                .target("X.y").type(ChaosExperiment.TYPE_EXCEPTION).enabled(true).build());
        assertThat(controller.get("X.y").getData().getTarget()).isEqualTo("X.y");
        assertThat(controller.get("not.exist").getData()).isNull();
    }

    @Test
    @DisplayName("update 覆盖已存在实验")
    void update_overwrites() {
        controller.register(ChaosExperiment.builder()
                .target("X.y").type(ChaosExperiment.TYPE_LATENCY).latencyMs(50L).enabled(true).build());
        controller.update("X.y", ChaosExperiment.builder()
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .exceptionClass("java.lang.IllegalStateException")
                .enabled(true)
                .build());
        ChaosExperiment exp = controller.get("X.y").getData();
        assertThat(exp.getType()).isEqualTo(ChaosExperiment.TYPE_EXCEPTION);
        assertThat(exp.getTarget()).isEqualTo("X.y");
    }

    @Test
    @DisplayName("toggle 修改 enabled 字段")
    void toggle() {
        controller.register(ChaosExperiment.builder()
                .target("X.y").type(ChaosExperiment.TYPE_LATENCY).latencyMs(10L).enabled(true).build());
        controller.toggle("X.y", false);
        assertThat(controller.get("X.y").getData().isEnabled()).isFalse();
        controller.toggle("X.y", true);
        assertThat(controller.get("X.y").getData().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("toggle 实验不存在时抛 IllegalArgumentException")
    void toggle_missing() {
        assertThatThrownBy(() -> controller.toggle("not.exist", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("unregister 后 list 为空")
    void unregister() {
        controller.register(ChaosExperiment.builder()
                .target("X.y").type("LATENCY").enabled(true).build());
        controller.unregister("X.y");
        assertThat(controller.list().getData()).isEmpty();
    }

    @Test
    @DisplayName("history 反映最近事件, clearHistory 清空")
    void historyAndClear() {
        when(featureFlagService.isEnabled(any())).thenReturn(false);
        controller.register(ChaosExperiment.builder()
                .target("X.y").type(ChaosExperiment.TYPE_EXCEPTION).enabled(true).build());
        controller.dryRun("X.y");
        controller.dryRun("X.y");
        assertThat(controller.history().getData()).hasSize(2);
        controller.clearHistory();
        assertThat(controller.history().getData()).isEmpty();
    }

    @Test
    @DisplayName("dry-run INJECTED 返回 outcome=INJECTED, error 包含异常信息")
    void dryRun_injected() {
        when(featureFlagService.isEnabled(any())).thenReturn(true);
        controller.register(ChaosExperiment.builder()
                .target("X.y")
                .type(ChaosExperiment.TYPE_EXCEPTION)
                .exceptionClass("java.lang.IllegalStateException")
                .enabled(true)
                .build());
        Map<String, Object> data = controller.dryRun("X.y").getData();
        assertThat(data.get("target")).isEqualTo("X.y");
        assertThat(data.get("outcome")).isEqualTo(ChaosOutcome.INJECTED.name());
        assertThat((String) data.get("error")).contains("IllegalStateException");
    }

    @Test
    @DisplayName("dry-run 未注册实验返回 NOT_TRIGGERED")
    void dryRun_notTriggered() {
        Map<String, Object> data = controller.dryRun("not.exist").getData();
        assertThat(data.get("outcome")).isEqualTo(ChaosOutcome.NOT_TRIGGERED.name());
    }

    @Test
    @DisplayName("dry-run Feature flag 关闭时返回 BLOCKED_BY_FLAG")
    void dryRun_blocked() {
        when(featureFlagService.isEnabled(eq(FeatureFlag.CANARY_DEPLOY))).thenReturn(false);
        controller.register(ChaosExperiment.builder()
                .target("X.y").type(ChaosExperiment.TYPE_LATENCY).latencyMs(10L).enabled(true).build());
        Map<String, Object> data = controller.dryRun("X.y").getData();
        assertThat(data.get("outcome")).isEqualTo(ChaosOutcome.BLOCKED_BY_FLAG.name());
    }
}
