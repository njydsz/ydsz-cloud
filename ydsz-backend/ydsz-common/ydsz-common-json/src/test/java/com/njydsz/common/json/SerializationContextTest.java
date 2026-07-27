package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.provider.SerializationContext;

import com.njydsz.common.json.provider.SerializationProvider;
/**
 * SerializationContext 上下文管理测试。
 *
 * @since 1.0.0
 */
class SerializationContextTest {

    @Test
    void testDefaultValues() {
        SerializationContext ctx = new SerializationContext();
        assertFalse(ctx.writeNulls);
        assertFalse(ctx.prettyPrint);
        assertFalse(ctx.serializeEnumUsingOrdinal);
        assertEquals("REF", ctx.circularRefStrategy);
        assertNull(ctx.namingStrategy);
    }

    @Test
    void testReset() {
        SerializationContext ctx = new SerializationContext();
        ctx.writeNulls = true;
        ctx.prettyPrint = true;
        ctx.serializeEnumUsingOrdinal = true;
        ctx.circularRefStrategy = "IGNORE";

        ctx.reset();

        assertFalse(ctx.writeNulls);
        assertFalse(ctx.prettyPrint);
        assertFalse(ctx.serializeEnumUsingOrdinal);
        assertEquals("REF", ctx.circularRefStrategy);
    }

    @Test
    void testCaptureAndApply() {
        // 设置 Provider 值
        SerializationProvider.setWriteNulls(true);
        SerializationProvider.setPrettyPrint(true);

        // 捕获到 context
        SerializationContext ctx = new SerializationContext();
        ctx.captureFromProvider();
        assertTrue(ctx.writeNulls);
        assertTrue(ctx.prettyPrint);

        // 修改 Provider 值
        SerializationProvider.setWriteNulls(false);
        SerializationProvider.setPrettyPrint(false);

        // 从 context 恢复
        ctx.applyToProvider();
        assertTrue(SerializationProvider.isWriteNulls());
        assertTrue(SerializationProvider.isPrettyPrint());

        // 清理
        SerializationProvider.setWriteNulls(false);
        SerializationProvider.setPrettyPrint(false);
    }

    @Test
    void testEstimateThreadLocalMemory() {
        long memory = SerializationContext.estimateThreadLocalMemory();
        assertTrue(memory > 0);
        // 至少包含 SerializationContext 本身 + StringBuilder + JSONWriter + IdentityHashMap
        assertTrue(memory > 1000);
    }

    @Test
    void testClearDoesNotThrow() {
        assertDoesNotThrow(() -> SerializationContext.clear());
    }
}
