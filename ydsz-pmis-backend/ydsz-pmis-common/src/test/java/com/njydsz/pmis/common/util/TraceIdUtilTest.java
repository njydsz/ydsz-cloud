package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceIdUtil 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("TraceIdUtil 测试")
class TraceIdUtilTest {

    @BeforeEach
    @AfterEach
    void cleanUp() {
        TraceIdUtil.clear();
    }

    // ==================== generate ====================

    @Test
    @DisplayName("generate - 应生成 16 位非空字符串")
    void generate_shouldReturn16CharString() {
        String traceId = TraceIdUtil.generate();
        assertNotNull(traceId);
        assertEquals(16, traceId.length());
        assertFalse(traceId.contains("-"), "不应包含连字符");
    }

    @Test
    @DisplayName("generate - 多次调用应返回不同值")
    void generate_shouldReturnDifferentValues() {
        String id1 = TraceIdUtil.generate();
        String id2 = TraceIdUtil.generate();
        String id3 = TraceIdUtil.generate();
        assertNotEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertNotEquals(id2, id3);
    }

    // ==================== set / get ====================

    @Test
    @DisplayName("set/get - 设置后应能获取到相同值")
    void setAndGet_shouldWork() {
        TraceIdUtil.set("test-trace-id-001");
        assertEquals("test-trace-id-001", TraceIdUtil.get());
    }

    @Test
    @DisplayName("get - 未设置时应返回空字符串")
    void get_shouldReturnEmptyWhenNotSet() {
        assertEquals("", TraceIdUtil.get());
    }

    // ==================== getOrCreate ====================

    @Test
    @DisplayName("getOrCreate - 未设置时应自动生成并返回")
    void getOrCreate_shouldGenerateWhenNotSet() {
        String traceId = TraceIdUtil.getOrCreate();
        assertNotNull(traceId);
        assertEquals(16, traceId.length());
        assertFalse(traceId.isEmpty());
        // 再次调用应返回相同值
        assertEquals(traceId, TraceIdUtil.getOrCreate());
    }

    @Test
    @DisplayName("getOrCreate - 已设置时应返回已设置的值")
    void getOrCreate_shouldReturnExistingValue() {
        TraceIdUtil.set("my-custom-id");
        assertEquals("my-custom-id", TraceIdUtil.getOrCreate());
    }

    // ==================== clear ====================

    @Test
    @DisplayName("clear - 清除后 get 应返回空字符串")
    void clear_shouldResetToEmpty() {
        TraceIdUtil.set("test-trace-id");
        TraceIdUtil.clear();
        assertEquals("", TraceIdUtil.get());
    }

    @Test
    @DisplayName("clear - 清除后 getOrCreate 应生成新 ID")
    void clear_shouldAllowRegeneration() {
        TraceIdUtil.set("test-trace-id");
        TraceIdUtil.clear();
        String newId = TraceIdUtil.getOrCreate();
        assertNotNull(newId);
        assertEquals(16, newId.length());
        assertNotEquals("test-trace-id", newId);
    }

    // ==================== 线程隔离 ====================

    @Test
    @DisplayName("线程隔离 - 不同线程应有独立的 traceId")
    void threadIsolation_shouldHaveSeparateTraceIds() throws Exception {
        TraceIdUtil.set("main-thread-id");

        final String[] childTraceId = new String[1];
        Thread thread = new Thread(() -> {
            childTraceId[0] = TraceIdUtil.get();
        });
        thread.start();
        thread.join();

        // 子线程没有设置 traceId，应返回空字符串
        assertEquals("", childTraceId[0]);
        // 主线程的 traceId 不变
        assertEquals("main-thread-id", TraceIdUtil.get());
    }

    @Test
    @DisplayName("线程隔离 - 子线程设置不影响主线程")
    void threadIsolation_shouldNotAffectOtherThreads() throws Exception {
        TraceIdUtil.set("main-thread-id");

        Thread thread = new Thread(() -> {
            TraceIdUtil.set("child-thread-id");
        });
        thread.start();
        thread.join();

        assertEquals("main-thread-id", TraceIdUtil.get());
    }

    // ==================== TRACE_ID_KEY ====================

    @Test
    @DisplayName("TRACE_ID_KEY - 常量应为 traceId")
    void traceIdKey_shouldBeCorrect() {
        assertEquals("traceId", TraceIdUtil.TRACE_ID_KEY);
    }
}