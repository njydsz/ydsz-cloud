package com.njydsz.pmis.common.core.retry;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RetryTemplate 单元测试
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
@DisplayName("RetryTemplate 重试模板测试")
class RetryTemplateTest {

    @Test
    @DisplayName("首次成功不应重试")
    void execute_firstSuccess() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        RetryTemplate template = RetryTemplate.builder()
                .maxRetries(3)
                .initialBackoff(Duration.ofMillis(1))
                .build();

        String result = template.execute("test", () -> {
            counter.incrementAndGet();
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("重试后成功应返回结果")
    void execute_retryThenSuccess() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        RetryTemplate template = RetryTemplate.builder()
                .maxRetries(3)
                .initialBackoff(Duration.ofMillis(1))
                .build();

        String result = template.execute("test", () -> {
            if (counter.incrementAndGet() < 3) {
                throw new IOException("Connection refused");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, counter.get());
    }

    @Test
    @DisplayName("超过最大重试次数应抛出异常")
    void execute_exceedMaxRetries() {
        RetryTemplate template = RetryTemplate.builder()
                .maxRetries(2)
                .initialBackoff(Duration.ofMillis(1))
                .build();

        assertThrows(IOException.class, () -> {
            template.execute("test", () -> {
                throw new IOException("Always fails");
            });
        });
    }

    @Test
    @DisplayName("不匹配的异常不应重试")
    void execute_nonMatchingExceptionShouldNotRetry() {
        AtomicInteger counter = new AtomicInteger(0);
        RetryTemplate template = RetryTemplate.builder()
                .maxRetries(3)
                .initialBackoff(Duration.ofMillis(1))
                .retryOn(e -> e instanceof IOException)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            template.execute("test", () -> {
                counter.incrementAndGet();
                throw new IllegalArgumentException("Not retryable");
            });
        });

        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("重试监听器应被调用")
    void execute_listenerShouldBeCalled() throws Exception {
        AtomicInteger retryCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        RetryTemplate template = RetryTemplate.builder()
                .maxRetries(3)
                .initialBackoff(Duration.ofMillis(1))
                .listener(new RetryTemplate.RetryListener() {
                    @Override
                    public void onRetry(String name, int attempt, Exception e) {
                        retryCount.incrementAndGet();
                    }

                    @Override
                    public void onSuccess(String name, int attempts) {
                        successCount.set(attempts);
                    }
                })
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        template.execute("test", () -> {
            if (callCount.incrementAndGet() < 2) {
                throw new IOException("retry");
            }
            return "ok";
        });

        assertEquals(1, retryCount.get());
        assertEquals(1, successCount.get());
    }
}
