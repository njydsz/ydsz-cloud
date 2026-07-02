package com.njydsz.pmis.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MDC TaskDecorator 单元测试（P1-9）
 *
 * <p>验证 CommonAutoConfiguration 注册的 TaskDecorator 能将主线程 MDC 上下文
 * （traceId 等）正确传递到 @Async 异步线程，解决 OperationLogAspect、事件监听器
 * 等异步方法丢失 traceId 的问题。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MDC TaskDecorator 异步线程上下文传递测试")
class MdcTaskDecoratorTest {

    private final CommonAutoConfiguration config = new CommonAutoConfiguration();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("异步线程应继承主线程 MDC traceId")
    void asyncThreadInheritsMdc() throws Exception {
        TaskDecorator decorator = config.mdcTaskDecorator();

        MDC.put("traceId", "main-trace-001");
        MDC.put("userId", "1001");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, String>> asyncMdc = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            asyncMdc.set(MDC.getCopyOfContextMap());
            latch.countDown();
        });

        // 在新线程中执行，模拟 @Async 异步线程
        Thread thread = new Thread(decorated);
        thread.start();
        latch.await();

        // 异步线程中的 MDC 应包含主线程的 traceId/userId
        assertThat(asyncMdc.get()).isNotNull();
        assertThat(asyncMdc.get().get("traceId")).isEqualTo("main-trace-001");
        assertThat(asyncMdc.get().get("userId")).isEqualTo("1001");

        // 主线程的 MDC 不受影响
        assertThat(MDC.get("traceId")).isEqualTo("main-trace-001");
    }

    @Test
    @DisplayName("主线程无 MDC 时异步线程也不报错")
    void noMdcInMainThreadNoError() throws Exception {
        TaskDecorator decorator = config.mdcTaskDecorator();

        // 主线程无 MDC
        assertThat(MDC.getCopyOfContextMap()).isNull();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> asyncTraceId = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            asyncTraceId.set(MDC.get("traceId"));
            latch.countDown();
        });

        Thread thread = new Thread(decorated);
        thread.start();
        latch.await();

        // 异步线程无 traceId，但不报错
        assertThat(asyncTraceId.get()).isNull();
    }

    @Test
    @DisplayName("装饰的 Runnable 执行后异步线程 MDC 应被清理")
    void mdcClearedAfterRun() throws Exception {
        TaskDecorator decorator = config.mdcTaskDecorator();

        MDC.put("traceId", "main-trace-002");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, String>> mdcAfterRun = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            // 在执行业务逻辑期间 MDC 存在
            assertThat(MDC.get("traceId")).isEqualTo("main-trace-002");
            // 业务逻辑执行完，MDC 会在 finally 中清理
        });

        // 在新线程执行，执行后立即检查 MDC
        Thread thread = new Thread(() -> {
            try {
                decorated.run();
            } finally {
                mdcAfterRun.set(MDC.getCopyOfContextMap());
                latch.countDown();
            }
        });
        thread.start();
        latch.await();

        // 异步线程执行完后 MDC 应被清理
        assertThat(mdcAfterRun.get()).isNull();
    }

    @Test
    @DisplayName("装饰的 Runnable 抛异常后 MDC 也应被清理")
    void mdcClearedOnException() throws Exception {
        TaskDecorator decorator = config.mdcTaskDecorator();

        MDC.put("traceId", "main-trace-003");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, String>> mdcAfterException = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            throw new RuntimeException("boom");
        });

        Thread thread = new Thread(() -> {
            try {
                decorated.run();
            } catch (RuntimeException ignored) {
                // 预期异常
            } finally {
                mdcAfterException.set(MDC.getCopyOfContextMap());
                latch.countDown();
            }
        });
        thread.start();
        latch.await();

        // 异步线程抛异常后 MDC 也应被清理
        assertThat(mdcAfterException.get()).isNull();
    }

    @Test
    @DisplayName("主线程多次捕获 MDC 应互不影响")
    void multipleDecorationsIndependent() throws Exception {
        TaskDecorator decorator = config.mdcTaskDecorator();

        // 第一次捕获
        MDC.put("traceId", "trace-A");
        CountDownLatch latchA = new CountDownLatch(1);
        AtomicReference<String> traceIdA = new AtomicReference<>();
        Runnable decoratedA = decorator.decorate(() -> {
            traceIdA.set(MDC.get("traceId"));
            latchA.countDown();
        });

        // 改变 MDC 后第二次捕获
        MDC.put("traceId", "trace-B");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> asyncTraceId = new AtomicReference<>();

        Runnable decoratedB = decorator.decorate(() -> {
            asyncTraceId.set(MDC.get("traceId"));
            latch.countDown();
        });

        // 启动两个异步线程分别执行，应各自捕获到 decorate 调用时的 MDC
        Thread threadA = new Thread(decoratedA);
        threadA.start();
        Thread threadB = new Thread(decoratedB);
        threadB.start();
        latchA.await();
        latch.await();

        // decoratedA 捕获的是 trace-A（第一次 decorate 时）
        assertThat(traceIdA.get()).isEqualTo("trace-A");
        // decoratedB 捕获的是 trace-B（在 decorate 调用时捕获）
        assertThat(asyncTraceId.get()).isEqualTo("trace-B");
    }
}
