package com.njydsz.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;
import java.util.Map;
/**
 * {@link AbstractYdszException} 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>异常构造与字段初始化</li>
 *   <li>getMessage() 懒加载 + DCL 线程安全</li>
 *   <li>i18n 消息解析（注入 resolver 前后对比）</li>
 *   <li>链式 data() 方法</li>
 *   <li>Builder 模式</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("AbstractYdszException 核心行为测试")
class AbstractYdszExceptionTest {

    @BeforeEach
    void setup() {
        AbstractYdszException.setMessageResolver(null);
    }

    @AfterEach
    void cleanup() {
        AbstractYdszException.setMessageResolver(null);
    }

    @Nested
    @DisplayName("异常构造与字段初始化")
    class ConstructionTest {

        @Test
        @DisplayName("BusinessException 默认构造函数：HTTP 400 + ERROR + BUSINESS")
        void testDefaultConstructor() {
            BusinessException ex = new BusinessException();
            assertEquals(400, ex.getHttpStatus());
            assertEquals(ExceptionLevel.ERROR, ex.getLevel());
            assertEquals(ExceptionCategory.BUSINESS, ex.getCategory());
            assertNotNull(ex.getTimestamp());
        }

        @Test
        @DisplayName("BusinessException 从 ExceptionCode 构造：字段正确映射")
        void testConstructFromExceptionCode() {
            BusinessException ex = new BusinessException(UnifiedExceptionCode.NOT_FOUND);
            assertEquals("A04051", ex.getCode());
            assertEquals("not.found", ex.getKey());
            assertEquals(404, ex.getHttpStatus());
        }

        @Test
        @DisplayName("SysException 默认 HTTP 500 + ERROR + SYSTEM")
        void testSysExceptionDefaults() {
            SysException ex = new SysException();
            assertEquals(500, ex.getHttpStatus());
            assertEquals(ExceptionLevel.ERROR, ex.getLevel());
            assertEquals(ExceptionCategory.SYSTEM, ex.getCategory());
        }

        @Test
        @DisplayName("SysException 从 ExceptionCode + cause 构造")
        void testSysExceptionWithCause() {
            Throwable cause = new RuntimeException("DB connection lost");
            SysException ex = new SysException(UnifiedExceptionCode.DATABASE_ERROR, cause);
            assertEquals("B01053", ex.getCode());
            assertEquals("database.error", ex.getKey());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("getMessage() 懒加载与 i18n 解析")
    class MessageResolutionTest {

        @Test
        @DisplayName("注入 resolver 前：getMessage() 返回 key 字符串（降级）")
        void testMessageWithoutResolver() {
            BusinessException ex = new BusinessException(UnifiedExceptionCode.NOT_FOUND);
            String msg = ex.getMessage();
            assertNotNull(msg);
            // resolver 未注入时，messageKey 非空但 resolver 为 null，应返回 messageKey
            assertEquals("not.found", msg);
        }

        @Test
        @DisplayName("注入 resolver 后：getMessage() 返回 i18n 解析后的消息")
        void testMessageWithResolver() {
            AbstractYdszException.setMessageResolver((key, params) -> {
                if ("not.found".equals(key)) {
                    return "资源不存在";
                }
                return key;
            });
            BusinessException ex = new BusinessException(UnifiedExceptionCode.NOT_FOUND);
            assertEquals("资源不存在", ex.getMessage());
        }

        @Test
        @DisplayName("直接设置 message 后：getMessage() 返回直接消息（优先级最高）")
        void testDirectMessageOverride() {
            AbstractYdszException.setMessageResolver((key, params) -> "should-not-be-used");
            BusinessException ex = new BusinessException(UnifiedExceptionCode.NOT_FOUND);
            ex.setMessage("自定义消息");
            assertEquals("自定义消息", ex.getMessage());
        }

        @Test
        @DisplayName("getMessage() 懒加载只解析一次（缓存生效）")
        void testLazyResolutionCached() {
            AtomicInteger callCount = new AtomicInteger(0);
            AbstractYdszException.setMessageResolver((key, params) -> {
                callCount.incrementAndGet();
                return "resolved-" + key;
            });
            BusinessException ex = new BusinessException(UnifiedExceptionCode.NOT_FOUND);
            ex.getMessage();
            ex.getMessage();
            ex.getMessage();
            assertEquals(1, callCount.get());
        }
    }

    @Nested
    @DisplayName("DCL 线程安全测试")
    class ConcurrencyTest {

        @Test
        @DisplayName("多线程并发调用 getMessage() 只解析一次")
        void testConcurrentGetMessage() throws InterruptedException {
            AtomicInteger callCount = new AtomicInteger(0);
            AbstractYdszException.setMessageResolver((key, params) -> {
                callCount.incrementAndGet();
                return "resolved-" + key;
            });
            BusinessException ex = new BusinessException(UnifiedExceptionCode.NOT_FOUND);

            int threadCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ex.getMessage();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            assertEquals(1, callCount.get(), "resolver should only be called once under concurrent access");
        }
    }

    @Nested
    @DisplayName("链式 data() 方法")
    class DataChainTest {

        @Test
        @DisplayName("data() 链式调用构建附加数据 Map")
        void testDataChain() {
            BusinessException ex = new BusinessException(UnifiedExceptionCode.FAIL)
                    .data("userId", 123)
                    .data("tenant", "acme");
            assertNotNull(ex.getExtData());
            assertInstanceOf(Map.class, ex.getExtData());
            Map<?, ?> dataMap = (Map<?, ?>) ex.getExtData();
            assertEquals(123, dataMap.get("userId"));
            assertEquals("acme", dataMap.get("tenant"));
        }

        @Test
        @DisplayName("data() 在 extData 为 null 时正确初始化")
        void testDataInitialization() {
            BusinessException ex = new BusinessException();
            assertNull(ex.getExtData());
            ex.data("key1", "value1");
            assertNotNull(ex.getExtData());
        }
    }

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTest {

        @Test
        @DisplayName("BusinessException.builder() 构建完整异常")
        void testBuilder() {
            BusinessException ex = BusinessException.builder()
                    .code("CUSTOM_CODE")
                    .key("custom.error")
                    .httpStatus(422)
                    .level(ExceptionLevel.WARN)
                    .category(ExceptionCategory.VALIDATION)
                    .message("自定义业务异常")
                    .build();

            assertEquals("CUSTOM_CODE", ex.getCode());
            assertEquals("custom.error", ex.getKey());
            assertEquals(422, ex.getHttpStatus());
            assertEquals(ExceptionLevel.WARN, ex.getLevel());
            assertEquals(ExceptionCategory.VALIDATION, ex.getCategory());
            assertEquals("自定义业务异常", ex.getMessage());
        }

        @Test
        @DisplayName("BusinessException.of(ExceptionCode) 工厂方法")
        void testOfFactory() {
            BusinessException ex = BusinessException.of(UnifiedExceptionCode.PARAM_ERROR);
            assertEquals("A01052", ex.getCode());
            assertEquals("param.error", ex.getKey());
            assertEquals(400, ex.getHttpStatus());
        }
    }

}
