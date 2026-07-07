package com.njydsz.pmis.cronjob.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link CronjobProperties} 单元测试。
 *
 * <p>覆盖核心方法 {@link CronjobProperties#normalizeTtl(Duration)} 的边界条件：
 * <ul>
 *   <li>null / 零 / 负数 → 返回默认值</li>
 *   <li>小于下限 → 返回下限</li>
 *   <li>大于上限 → 返回上限</li>
 *   <li>区间内 → 原值返回</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CronjobProperties 配置规整化测试")
class CronjobPropertiesTest {

    @Test
    @DisplayName("默认配置项应具有合理初值")
    void defaultValuesShouldBeReasonable() {
        CronjobProperties p = new CronjobProperties();
        assertEquals(Duration.ofMinutes(5), p.getJobLockTtl(), "默认锁 TTL 应为 5 分钟");
        assertEquals(Duration.ofSeconds(30), p.getJobLockTtlMin(), "下限应为 30 秒");
        assertEquals(Duration.ofHours(24), p.getJobLockTtlMax(), "上限应为 24 小时");
        assertEquals(8, p.getSchedulerPoolSize(), "线程池默认应为 8");
        assertEquals(30, p.getSchedulerAwaitTerminationSeconds(), "优雅关闭应为 30 秒");
    }

    @Test
    @DisplayName("null TTL 应返回全局默认值")
    void normalizeTtl_null_returnsDefault() {
        CronjobProperties p = new CronjobProperties();
        assertEquals(Duration.ofMinutes(5), p.normalizeTtl(null));
    }

    @Test
    @DisplayName("零值 TTL 应返回全局默认值")
    void normalizeTtl_zero_returnsDefault() {
        CronjobProperties p = new CronjobProperties();
        assertEquals(Duration.ofMinutes(5), p.normalizeTtl(Duration.ZERO));
    }

    @Test
    @DisplayName("负值 TTL 应返回全局默认值")
    void normalizeTtl_negative_returnsDefault() {
        CronjobProperties p = new CronjobProperties();
        assertEquals(Duration.ofMinutes(5), p.normalizeTtl(Duration.ofSeconds(-100)));
    }

    @Test
    @DisplayName("小于下限的 TTL 应收敛到下限")
    void normalizeTtl_belowMin_clampsToMin() {
        CronjobProperties p = new CronjobProperties();
        // 5 秒 < 30 秒下限 → 应返回 30 秒
        assertEquals(Duration.ofSeconds(30), p.normalizeTtl(Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("大于上限的 TTL 应收敛到上限")
    void normalizeTtl_aboveMax_clampsToMax() {
        CronjobProperties p = new CronjobProperties();
        // 48 小时 > 24 小时上限 → 应返回 24 小时
        assertEquals(Duration.ofHours(24), p.normalizeTtl(Duration.ofHours(48)));
    }

    @Test
    @DisplayName("区间内的 TTL 应原值返回")
    void normalizeTtl_inRange_returnsOriginal() {
        CronjobProperties p = new CronjobProperties();
        // 10 分钟在 [30s, 24h] 区间内 → 原值返回
        Duration tenMinutes = Duration.ofMinutes(10);
        assertEquals(tenMinutes, p.normalizeTtl(tenMinutes));
    }

    @Test
    @DisplayName("区间边界值（下限）应原值返回")
    void normalizeTtl_atMinBoundary_returnsOriginal() {
        CronjobProperties p = new CronjobProperties();
        assertEquals(Duration.ofSeconds(30), p.normalizeTtl(Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("区间边界值（上限）应原值返回")
    void normalizeTtl_atMaxBoundary_returnsOriginal() {
        CronjobProperties p = new CronjobProperties();
        assertEquals(Duration.ofHours(24), p.normalizeTtl(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("自定义全局默认值应被使用")
    void customDefaultApplied() {
        CronjobProperties p = new CronjobProperties();
        p.setJobLockTtl(Duration.ofMinutes(10));
        assertEquals(Duration.ofMinutes(10), p.normalizeTtl(null),
                "自定义全局默认 10 分钟应被应用");
    }

    @Test
    @DisplayName("自定义上下限应被遵守")
    void customBoundsRespected() {
        CronjobProperties p = new CronjobProperties();
        p.setJobLockTtlMin(Duration.ofMinutes(1));
        p.setJobLockTtlMax(Duration.ofMinutes(60));
        // 30 秒 < 1 分钟下限 → 收敛到 1 分钟
        assertEquals(Duration.ofMinutes(1), p.normalizeTtl(Duration.ofSeconds(30)));
        // 2 小时 > 1 小时上限 → 收敛到 1 小时
        assertEquals(Duration.ofMinutes(60), p.normalizeTtl(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("Lombok @Data 生成的 getter/setter 应可正常使用")
    void lombokDataWorks() {
        CronjobProperties p = new CronjobProperties();
        p.setSchedulerPoolSize(16);
        assertEquals(16, p.getSchedulerPoolSize());
        assertNotNull(p.toString(), "toString() 应非空");
    }
}
