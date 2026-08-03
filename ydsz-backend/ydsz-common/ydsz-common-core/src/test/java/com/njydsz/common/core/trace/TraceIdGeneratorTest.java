package com.njydsz.common.core.trace;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TraceIdGenerator} 单元测试
 *
 * <p>覆盖默认 UUID 策略、SPI 注入切换、reset 恢复、null 兜底等行为。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("TraceIdGenerator 统一入口测试")
class TraceIdGeneratorTest {

    @AfterEach
    void tearDown() {
        TraceIdGenerator.resetToDefault();
    }

    @Test
    @DisplayName("默认策略生成 32 位 UUID（无连字符）")
    void default_uuid32() {
        String id = TraceIdGenerator.generate();
        assertEquals(32, id.length());
        assertTrue(id.matches("^[0-9a-f]{32}$"), "uuid hex expected: " + id);
    }

    @Test
    @DisplayName("默认策略生成唯一 ID")
    void default_unique() {
        String a = TraceIdGenerator.generate();
        String b = TraceIdGenerator.generate();
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("setSupplier 切换策略后使用新策略")
    void setSupplier_customStrategy() {
        TraceIdGenerator.setSupplier(() -> "custom-trace-id");
        assertEquals("custom-trace-id", TraceIdGenerator.generate());
    }

    @Test
    @DisplayName("setSupplier(null) 恢复默认 UUID 策略")
    void setSupplier_nullRestoresDefault() {
        TraceIdGenerator.setSupplier(() -> "custom");
        TraceIdGenerator.setSupplier(null);
        assertEquals(32, TraceIdGenerator.generate().length());
    }

    @Test
    @DisplayName("resetToDefault 恢复默认策略")
    void resetToDefault() {
        TraceIdGenerator.setSupplier(() -> "custom");
        TraceIdGenerator.resetToDefault();
        assertEquals(32, TraceIdGenerator.generate().length());
    }

    @Test
    @DisplayName("getSupplier 返回当前策略")
    void getSupplier() {
        TraceIdSupplier supplier = () -> "x";
        TraceIdGenerator.setSupplier(supplier);
        assertEquals(supplier, TraceIdGenerator.getSupplier());
    }

    @Test
    @DisplayName("默认策略生成的 ID 为 32 位（UUID 策略特征）")
    void defaultSupplierIsUuidStyle() {
        // 默认策略为 UUID（32 位 hex），区别于 Snowflake（16 位 hex）
        assertEquals(32, TraceIdGenerator.generate().length());
    }
}
