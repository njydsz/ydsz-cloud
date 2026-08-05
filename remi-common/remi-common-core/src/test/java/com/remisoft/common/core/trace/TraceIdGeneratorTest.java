package com.remisoft.common.core.trace;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * TraceIdGenerator + SpanContext 单元测试
 *
 * <p>验证：生成算法、格式正确性、W3C/B3/SkyWalking 协议互转的正确性。
 *
 * @author remi-team
 * @since 1.8.0
 */
class TraceIdGeneratorTest {

    @Nested
    @DisplayName("TraceIdGenerator")
    class Generator {

        @Test
        @DisplayName("generateTraceId 返回 32 位小写十六进制字符串")
        void traceId_format() {
            String traceId = TraceIdGenerator.generateTraceId();
            assertNotNull(traceId);
            assertEquals(32, traceId.length());
            assertTrue(traceId.matches("^[0-9a-f]{32}$"),
                "traceId 应为 32 位小写 16 进制，实际值: " + traceId);
        }

        @Test
        @DisplayName("generateSpanId 返回 16 位小写十六进制字符串")
        void spanId_format() {
            String spanId = TraceIdGenerator.generateSpanId();
            assertNotNull(spanId);
            assertEquals(16, spanId.length());
            assertTrue(spanId.matches("^[0-9a-f]{16}$"),
                "spanId 应为 16 位小写 16 进制，实际值: " + spanId);
        }

        @RepeatedTest(100)
        @DisplayName("每次生成的 traceId 应唯一")
        void traceId_uniqueness() {
            String a = TraceIdGenerator.generateTraceId();
            String b = TraceIdGenerator.generateTraceId();
            assertNotEquals(a, b, "连续两次生成应不同");
        }

        @Test
        @DisplayName("traceparent 格式正确")
        void traceparent_format() {
            String tp = TraceIdGenerator.traceparentHeader(
                "0af7651916cd43dd8448eb211c80319c",
                "b7ad6b7169203331"
            );
            assertEquals("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01", tp);
        }

        @Test
        @DisplayName("newTraceparent 连续调用产生不同值")
        void newTraceparent_unique() {
            String a = TraceIdGenerator.newTraceparent();
            String b = TraceIdGenerator.newTraceparent();
            assertNotEquals(a, b);
        }
    }

    @Nested
    @DisplayName("SpanContext")
    class SpanCtx {

        @Test
        @DisplayName("newRoot() 创建新的 SpanContext")
        void newRoot() {
            SpanContext ctx = SpanContext.newRoot();
            assertNotNull(ctx.traceId());
            assertEquals(32, ctx.traceId().length());
            assertNotNull(ctx.spanId());
            assertEquals(16, ctx.spanId().length());
            assertTrue(ctx.isSampled());
        }

        @Test
        @DisplayName("newRoot(false) 设置 NOT_SAMPLED")
        void newRoot_notSampled() {
            SpanContext ctx = SpanContext.newRoot(false);
            assertFalse(ctx.isSampled());
            assertEquals("00", ctx.traceFlags());
        }

        @Test
        @DisplayName("toTraceparent() 往返序列化")
        void traceparent_roundTrip() {
            SpanContext original = SpanContext.newRoot();
            String header = original.toTraceparent();
            SpanContext parsed = SpanContext.fromTraceparent(header);

            assertEquals(original.traceId(), parsed.traceId());
            assertEquals(original.spanId(), parsed.spanId());
            assertEquals(original.traceFlags(), parsed.traceFlags());
        }

        @Test
        @DisplayName("fromTraceparent 解析 W3C header")
        void fromTraceparent_known() {
            SpanContext ctx = SpanContext.fromTraceparent(
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
            );
            assertEquals("0af7651916cd43dd8448eb211c80319c", ctx.traceId());
            assertEquals("b7ad6b7169203331", ctx.spanId());
            assertTrue(ctx.isSampled());
        }

        @Test
        @DisplayName("B3 single header 互转")
        void b3_roundTrip() {
            SpanContext original = SpanContext.newRoot(true);
            String b3 = original.toB3Single();
            assertTrue(b3.contains("-"));

            SpanContext parsed = SpanContext.fromB3Single(b3);
            assertEquals(original.traceId(), parsed.traceId());
            assertEquals(original.spanId(), parsed.spanId());
        }

        @Test
        @DisplayName("SkyWalking 格式")
        void skyWalking_format() {
            SpanContext ctx = SpanContext.newRoot();
            String sw = ctx.toSkyWalking();
            assertEquals(ctx.traceId() + ".0.0.1", sw);
        }

        @Test
        @DisplayName("newChild() 保持 traceId 不变，spanId 更新")
        void newChild() {
            SpanContext parent = SpanContext.newRoot();
            SpanContext child = parent.newChild();

            assertEquals(parent.traceId(), child.traceId());
            assertNotEquals(parent.spanId(), child.spanId());
        }

        @Test
        @DisplayName("withTraceStateEntry 添加状态条目")
        void withTraceStateEntry() {
            SpanContext original = SpanContext.newRoot();
            SpanContext extended = original.withTraceStateEntry("tdm", "trace:abc");

            assertEquals(1, extended.traceState().size());
            assertEquals("tdm", extended.traceState().get(0).key());
        }

        @Test
        @DisplayName("null traceId 应该抛出")
        void nullTraceId_throws() {
            assertThrows(NullPointerException.class,
                () -> new SpanContext(null, "span1234567890123")
            );
        }
    }
}
