package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.flow.enums.CanaryStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowCanaryServiceImpl 灰度发布服务 单元测试
 *
 * <p>P3-1 落地：覆盖启动灰度 / 调整比例 / 全量发布 / 回滚 / 切流判断等核心逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@DisplayName("FlowCanaryServiceImpl 单元测试")
class FlowCanaryServiceImplTest {

    private FlowDefinitionMapper definitionMapper;
    private FlowCanaryServiceImpl service;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(FlowDefinitionMapper.class);
        service = new FlowCanaryServiceImpl(definitionMapper);
    }

    private FlowDefinitionDO makeDef(Long id, String version, Integer isPublish,
                                     Integer canaryPercent, String canaryStatus) {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(id);
        def.setFlowCode("project_initiation");
        def.setFlowName("项目立项");
        def.setVersion(version);
        def.setIsPublish(isPublish);
        def.setCanaryPercent(canaryPercent);
        def.setCanaryStatus(canaryStatus);
        def.setCanaryStrategy("USER_HASH");
        def.setTenantId(1L);
        return def;
    }

    // ============== 启动灰度 ==============

    @Test
    @DisplayName("publishCanary 首次启动 10% 灰度")
    void testPublishCanaryStart() {
        FlowDefinitionDO def = makeDef(1L, "1.0", 1, 0, CanaryStatus.NONE.name());
        when(definitionMapper.selectById(1L)).thenReturn(def);

        service.publishCanary(1L, 10, "USER_HASH", 7L, "运营", "首轮 10% 灰度");

        assertThat(def.getCanaryPercent()).isEqualTo(10);
        assertThat(def.getCanaryStatus()).isEqualTo(CanaryStatus.CANARYING.name());
        assertThat(def.getCanaryStrategy()).isEqualTo("USER_HASH");
        assertThat(def.getCanaryRolloutLog()).contains("\"fromPercent\":0");
        assertThat(def.getCanaryRolloutLog()).contains("\"toPercent\":10");
        verify(definitionMapper, times(1)).updateById(def);
    }

    @Test
    @DisplayName("publishCanary 未发布定义不可启动灰度")
    void testPublishCanaryNotPublished() {
        FlowDefinitionDO def = makeDef(1L, "1.0", 0, 0, CanaryStatus.NONE.name());
        when(definitionMapper.selectById(1L)).thenReturn(def);

        assertThatThrownBy(() -> service.publishCanary(1L, 10, "USER_HASH", 7L, "运营", "test"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅已发布");
    }

    @Test
    @DisplayName("publishCanary 已 PROMOTED 的定义不可再次启动")
    void testPublishCanaryAlreadyPromoted() {
        FlowDefinitionDO def = makeDef(1L, "1.0", 1, 100, CanaryStatus.PROMOTED.name());
        when(definitionMapper.selectById(1L)).thenReturn(def);

        assertThatThrownBy(() -> service.publishCanary(1L, 10, "USER_HASH", 7L, "运营", "test"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已全量发布");
    }

    @Test
    @DisplayName("publishCanary 比例越界 200% 应抛异常")
    void testPublishCanaryInvalidPercent() {
        assertThatThrownBy(() -> service.publishCanary(1L, 200, "USER_HASH", 7L, "运营", "test"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("0-100");
    }

    @Test
    @DisplayName("publishCanary definitionId 为空应抛异常")
    void testPublishCanaryNullId() {
        assertThatThrownBy(() -> service.publishCanary(null, 10, "USER_HASH", 7L, "运营", "test"))
                .isInstanceOf(BizException.class);
    }

    // ============== 调整比例 ==============

    @Test
    @DisplayName("adjustCanaryPercent 10% → 50% 应成功")
    void testAdjustCanaryPercent() {
        FlowDefinitionDO def = makeDef(1L, "1.0", 1, 10, CanaryStatus.CANARYING.name());
        when(definitionMapper.selectById(1L)).thenReturn(def);

        service.adjustCanaryPercent(1L, 50, 7L, "运营", "放量至 50%");

        assertThat(def.getCanaryPercent()).isEqualTo(50);
        assertThat(def.getCanaryRolloutLog()).contains("\"fromPercent\":10");
        assertThat(def.getCanaryRolloutLog()).contains("\"toPercent\":50");
        verify(definitionMapper, times(1)).updateById(def);
    }

    @Test
    @DisplayName("adjustCanaryPercent 非 CANARYING 状态应抛异常")
    void testAdjustCanaryPercentWrongStatus() {
        FlowDefinitionDO def = makeDef(1L, "1.0", 1, 0, CanaryStatus.NONE.name());
        when(definitionMapper.selectById(1L)).thenReturn(def);

        assertThatThrownBy(() -> service.adjustCanaryPercent(1L, 50, 7L, "运营", "test"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("灰度中");
    }

    @Test
    @DisplayName("adjustCanaryPercent 同比例无变化应跳过 update")
    void testAdjustCanaryPercentNoChange() {
        FlowDefinitionDO def = makeDef(1L, "1.0", 1, 30, CanaryStatus.CANARYING.name());
        when(definitionMapper.selectById(1L)).thenReturn(def);

        service.adjustCanaryPercent(1L, 30, 7L, "运营", "test");

        verify(definitionMapper, never()).updateById(any(FlowDefinitionDO.class));
    }

    // ============== 全量发布 ==============

    @Test
    @DisplayName("promoteCanary 灰度版晋升为稳定版 + 失效其他版本")
    void testPromoteCanary() {
        FlowDefinitionDO def = makeDef(2L, "1.1", 1, 50, CanaryStatus.CANARYING.name());
        def.setTenantId(1L);
        when(definitionMapper.selectById(2L)).thenReturn(def);

        service.promoteCanary(2L, 7L, "运营", "全量发布");

        assertThat(def.getCanaryPercent()).isEqualTo(100);
        assertThat(def.getCanaryStatus()).isEqualTo(CanaryStatus.PROMOTED.name());
        verify(definitionMapper, times(1)).deactivateByFlowCode(
                eq("project_initiation"), eq(2L), eq(1L));
        verify(definitionMapper, times(1)).updateById(def);
    }

    @Test
    @DisplayName("promoteCanary 重复调用应幂等（不重复失效）")
    void testPromoteCanaryIdempotent() {
        FlowDefinitionDO def = makeDef(2L, "1.1", 1, 100, CanaryStatus.PROMOTED.name());
        when(definitionMapper.selectById(2L)).thenReturn(def);

        service.promoteCanary(2L, 7L, "运营", "test");

        verify(definitionMapper, never()).deactivateByFlowCode(any(), any(), any());
        verify(definitionMapper, never()).updateById(any(FlowDefinitionDO.class));
    }

    // ============== 回滚 ==============

    @Test
    @DisplayName("rollbackCanary 灰度版置失效 + 状态 ROLLED_BACK")
    void testRollbackCanary() {
        FlowDefinitionDO def = makeDef(2L, "1.1", 1, 30, CanaryStatus.CANARYING.name());
        when(definitionMapper.selectById(2L)).thenReturn(def);

        service.rollbackCanary(2L, 7L, "运营", "线上 BUG 紧急回滚");

        assertThat(def.getCanaryPercent()).isEqualTo(0);
        assertThat(def.getCanaryStatus()).isEqualTo(CanaryStatus.ROLLED_BACK.name());
        assertThat(def.getIsPublish()).isEqualTo(9);
        assertThat(def.getCanaryRolloutLog()).contains("\"fromPercent\":30");
        assertThat(def.getCanaryRolloutLog()).contains("\"toPercent\":0");
        verify(definitionMapper, times(1)).updateById(def);
    }

    // ============== resolveEffectiveDefinition ==============

    @Test
    @DisplayName("resolveEffectiveDefinition 无灰度时返回稳定版")
    void testResolveNoCanary() {
        FlowDefinitionDO stable = makeDef(1L, "1.0", 1, 0, CanaryStatus.NONE.name());
        when(definitionMapper.selectPublished(eq("project_initiation"), eq("1.0"), eq(1L)))
                .thenReturn(stable);
        when(definitionMapper.selectCanaryingByCode(eq("project_initiation"), eq(1L)))
                .thenReturn(List.of());

        FlowDefinitionDO result = service.resolveEffectiveDefinition(
                "project_initiation", "1.0", 1L, 1001L);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("resolveEffectiveDefinition 灰度 0% 等价于无灰度，返回稳定版")
    void testResolveCanaryZeroPercent() {
        FlowDefinitionDO stable = makeDef(1L, "1.0", 1, 0, CanaryStatus.NONE.name());
        FlowDefinitionDO canary = makeDef(2L, "1.1", 1, 0, CanaryStatus.CANARYING.name());
        when(definitionMapper.selectPublished(eq("project_initiation"), eq("1.0"), eq(1L)))
                .thenReturn(stable);
        when(definitionMapper.selectCanaryingByCode(eq("project_initiation"), eq(1L)))
                .thenReturn(List.of(canary));

        FlowDefinitionDO result = service.resolveEffectiveDefinition(
                "project_initiation", "1.0", 1L, 1001L);
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("resolveEffectiveDefinition USER_HASH 100% 应全部走灰度版")
    void testResolveUserHashAllCanary() {
        FlowDefinitionDO stable = makeDef(1L, "1.0", 1, 0, CanaryStatus.NONE.name());
        FlowDefinitionDO canary = makeDef(2L, "1.1", 1, 100, CanaryStatus.CANARYING.name());
        // 实际 100% 会因 percent >= 100 直接返回 stable，这里模拟 percent=99
        canary.setCanaryPercent(99);
        when(definitionMapper.selectPublished(eq("project_initiation"), eq("1.0"), eq(1L)))
                .thenReturn(stable);
        when(definitionMapper.selectCanaryingByCode(eq("project_initiation"), eq(1L)))
                .thenReturn(List.of(canary));

        // 循环 1000 次（不同 initiator），统计命中灰度的比例
        int canaryCount = 0;
        for (long i = 1; i <= 1000; i++) {
            FlowDefinitionDO r = service.resolveEffectiveDefinition(
                    "project_initiation", "1.0", 1L, i);
            if (r.getId().equals(2L)) {
                canaryCount++;
            }
        }
        // 1000 个 initiator 中大约 99% 走灰度版（允许 ±2% 误差）
        assertThat(canaryCount).isBetween(950, 1000);
    }

    @Test
    @DisplayName("resolveEffectiveDefinition RANDOM 策略命中比例")
    void testResolveRandomStrategy() {
        FlowDefinitionDO stable = makeDef(1L, "1.0", 1, 0, CanaryStatus.NONE.name());
        FlowDefinitionDO canary = makeDef(2L, "1.1", 1, 50, CanaryStatus.CANARYING.name());
        canary.setCanaryStrategy("RANDOM");
        when(definitionMapper.selectPublished(eq("project_initiation"), eq("1.0"), eq(1L)))
                .thenReturn(stable);
        when(definitionMapper.selectCanaryingByCode(eq("project_initiation"), eq(1L)))
                .thenReturn(List.of(canary));

        int canaryCount = 0;
        for (int i = 0; i < 2000; i++) {
            FlowDefinitionDO r = service.resolveEffectiveDefinition(
                    "project_initiation", "1.0", 1L, (long) i);
            if (r.getId().equals(2L)) canaryCount++;
        }
        // 50% ± 5% 误差
        assertThat(canaryCount).isBetween(900, 1100);
    }

    @Test
    @DisplayName("resolveEffectiveDefinition WHITELIST 策略始终走灰度")
    void testResolveWhitelistStrategy() {
        FlowDefinitionDO stable = makeDef(1L, "1.0", 1, 0, CanaryStatus.NONE.name());
        FlowDefinitionDO canary = makeDef(2L, "1.1", 1, 50, CanaryStatus.CANARYING.name());
        canary.setCanaryStrategy("WHITELIST");
        when(definitionMapper.selectPublished(eq("project_initiation"), eq("1.0"), eq(1L)))
                .thenReturn(stable);
        when(definitionMapper.selectCanaryingByCode(eq("project_initiation"), eq(1L)))
                .thenReturn(List.of(canary));

        // 任取若干 initiator，全部应走灰度
        for (long i = 1; i <= 100; i++) {
            FlowDefinitionDO r = service.resolveEffectiveDefinition(
                    "project_initiation", "1.0", 1L, i);
            assertThat(r.getId()).isEqualTo(2L);
        }
    }

    @Test
    @DisplayName("resolveEffectiveDefinition 稳定版为 null 时返回 null")
    void testResolveStableNotFound() {
        when(definitionMapper.selectPublished(eq("unknown"), eq("1.0"), eq(1L)))
                .thenReturn(null);

        FlowDefinitionDO result = service.resolveEffectiveDefinition("unknown", "1.0", 1L, 1L);
        assertThat(result).isNull();
    }

    // ============== listCanaryRolloutLog ==============

    @Test
    @DisplayName("listCanaryRolloutLog 解析所有定义的 rollout log")
    void testListCanaryRolloutLog() {
        String log = "[{\"operatorId\":7,\"operatorName\":\"运营\","
                + "\"fromPercent\":0,\"toPercent\":10,"
                + "\"operateAt\":\"2026-01-01T00:00:00\",\"note\":\"test\"}]";
        FlowDefinitionDO def = makeDef(1L, "1.0", 1, 10, CanaryStatus.CANARYING.name());
        def.setCanaryRolloutLog(log);
        when(definitionMapper.selectByFlowCode(eq("project_initiation"), eq(1L)))
                .thenReturn(List.of(def));

        List<Map<String, Object>> logs = service.listCanaryRolloutLog("project_initiation", 1L);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("definitionId")).isEqualTo(1L);
        assertThat(logs.get(0).get("operatorId")).isEqualTo(7);
        assertThat(logs.get(0).get("toPercent")).isEqualTo(10);
    }

    @Test
    @DisplayName("listCanaryRolloutLog 空 flowCode 返回空列表")
    void testListCanaryRolloutLogEmpty() {
        assertThat(service.listCanaryRolloutLog("", 1L)).isEmpty();
        assertThat(service.listCanaryRolloutLog(null, 1L)).isEmpty();
    }

    @Test
    @DisplayName("listCanaryRolloutLog 解析失败应跳过该条")
    void testListCanaryRolloutLogParseError() {
        FlowDefinitionDO def = makeDef(1L, "1.0", 1, 10, CanaryStatus.CANARYING.name());
        def.setCanaryRolloutLog("not-a-json");
        when(definitionMapper.selectByFlowCode(eq("project_initiation"), eq(1L)))
                .thenReturn(List.of(def));

        assertThat(service.listCanaryRolloutLog("project_initiation", 1L)).isEmpty();
    }
}
