package com.njydsz.common.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RequestContext} 单元测试。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("RequestContext 测试")
class RequestContextTest {

    @AfterEach
    void cleanup() {
        RequestContext.clear();
    }

    @Nested
    @DisplayName("基本读写")
    class BasicReadWrite {

        @Test
        @DisplayName("设置并获取 userId")
        void setAndGetUserId() {
            RequestContext.setUserId("user-001");
            assertThat(RequestContext.getUserId()).isEqualTo("user-001");
        }

        @Test
        @DisplayName("设置并获取 tenantId")
        void setAndGetTenantId() {
            RequestContext.setTenantId("tenant-001");
            assertThat(RequestContext.getTenantId()).isEqualTo("tenant-001");
        }

        @Test
        @DisplayName("设置并获取 traceId")
        void setAndGetTraceId() {
            RequestContext.setTraceId("trace-001");
            assertThat(RequestContext.getTraceId()).isEqualTo("trace-001");
        }

        @Test
        @DisplayName("未设置时返回 null")
        void getWhenNotSet_returnsNull() {
            assertThat(RequestContext.getUserId()).isNull();
            assertThat(RequestContext.getTenantId()).isNull();
            assertThat(RequestContext.getTraceId()).isNull();
        }

        @Test
        @DisplayName("put null 值时移除 key")
        void putNullValue_removesKey() {
            RequestContext.setUserId("user-001");
            assertThat(RequestContext.getUserId()).isEqualTo("user-001");

            RequestContext.put("userId", null);
            assertThat(RequestContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("put null key 抛出 NullPointerException")
        void putNullKey_throwsNpe() {
            assertThatThrownBy(() -> RequestContext.put((String) null, "value"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("强类型 ContextKey")
    class ContextKeyOperations {

        private static final ContextKey<String> USER_NAME = ContextKey.of("userName", String.class);
        private static final ContextKey<Integer> USER_AGE = ContextKey.of("userAge", Integer.class, 0);

        @Test
        @DisplayName("通过 ContextKey 设置和获取值")
        void putAndGetByKey() {
            RequestContext.put(USER_NAME, "Alice");
            assertThat(RequestContext.get(USER_NAME)).isEqualTo("Alice");
        }

        @Test
        @DisplayName("通过 ContextKey 获取 Optional")
        void getOptionalByKey() {
            RequestContext.put(USER_NAME, "Bob");
            Optional<String> opt = RequestContext.getOptional(USER_NAME);
            assertThat(opt).isPresent().contains("Bob");
        }

        @Test
        @DisplayName("ContextKey 带默认值")
        void getWithDefault() {
            ContextKey<String> key = ContextKey.of("missing", String.class, "default");
            assertThat(RequestContext.get(key)).isNull(); // 默认值仅在 ContextKey.get() 时生效
            assertThat(key.get()).isEqualTo("default");
        }

        @Test
        @DisplayName("类型不匹配时抛出 IllegalStateException")
        void typeMismatch_throwsException() {
            RequestContext.put("userAge", "not-an-integer");
            assertThatThrownBy(() -> RequestContext.get(USER_AGE))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ContextKey equals/hashCode 基于 name 和 type")
        void contextKeyEquals() {
            ContextKey<String> key1 = ContextKey.of("key1", String.class);
            ContextKey<String> key2 = ContextKey.of("key1", String.class);
            ContextKey<String> key3 = ContextKey.of("key2", String.class);

            assertThat(key1).isEqualTo(key2);
            assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
            assertThat(key1).isNotEqualTo(key3);
        }

    }

    @Nested
    @DisplayName("上下文清理")
    class Cleanup {

        @Test
        @DisplayName("clear 清空所有上下文")
        void clear_removesAll() {
            RequestContext.setUserId("user-001");
            RequestContext.setTenantId("tenant-001");
            RequestContext.clear();
            assertThat(RequestContext.getUserId()).isNull();
            assertThat(RequestContext.getTenantId()).isNull();
        }

        @Test
        @DisplayName("runAndClear 自动清理 Supplier")
        void runAndClear_supplier() {
            String result = RequestContext.runAndClear(() -> {
                RequestContext.setUserId("user-001");
                return RequestContext.getUserId();
            });
            assertThat(result).isEqualTo("user-001");
            assertThat(RequestContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("runAndClear 自动清理 Runnable")
        void runAndClear_runnable() {
            RequestContext.runAndClear(() -> RequestContext.setUserId("user-001"));
            assertThat(RequestContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("CleanupGuard try-with-resources 自动清理")
        void cleanupGuard() {
            try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard()) {
                RequestContext.setUserId("user-001");
                assertThat(RequestContext.getUserId()).isEqualTo("user-001");
            }
            assertThat(RequestContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("runAndClear 异常时也清理")
        void runAndClear_exceptionStillCleans() {
            assertThatThrownBy(() -> RequestContext.runAndClear(() -> {
                RequestContext.setUserId("user-001");
                throw new RuntimeException("test error");
            })).isInstanceOf(RuntimeException.class);
            assertThat(RequestContext.getUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("异步上下文传播（已废弃 API 的向后兼容测试）")
    class AsyncPropagation {

        @Test
        @DisplayName("capture + wrapCallable 传播上下文到子线程")
        void captureAndWrapCallable() throws Exception {
            RequestContext.setUserId("user-001");
            RequestContext.setTenantId("tenant-001");

            Map<String, Object> captured = RequestContext.snapshot();
            RequestContext.clear();

            java.util.concurrent.Callable<String> task = RequestContext.wrapCallable(
                    () -> RequestContext.getUserId() + "|" + RequestContext.getTenantId(),
                    captured);

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    return "error";
                }
            });

            assertThat(future.get()).isEqualTo("user-001|tenant-001");
        }

        @Test
        @DisplayName("runWithContext 在指定上下文中执行后清理")
        void runWithContext() {
            RequestContext.setUserId("user-001");
            Map<String, Object> captured = RequestContext.snapshot();
            RequestContext.clear();

            String result = RequestContext.runWithContext(captured, () -> RequestContext.getUserId());
            assertThat(result).isEqualTo("user-001");
            assertThat(RequestContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("snapshot 返回当前上下文副本")
        void snapshot() {
            RequestContext.setUserId("user-001");
            Map<String, Object> snap = RequestContext.snapshot();
            assertThat(snap).containsEntry("userId", "user-001");
            // snapshot 不影响原始上下文
            assertThat(RequestContext.getUserId()).isEqualTo("user-001");
        }
    }
}
