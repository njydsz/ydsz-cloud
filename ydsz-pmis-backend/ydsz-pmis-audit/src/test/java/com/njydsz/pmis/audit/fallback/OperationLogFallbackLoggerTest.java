package com.njydsz.pmis.audit.fallback;

import com.njydsz.pmis.common.event.OperationLogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link OperationLogFallbackLogger} 单元测试
 *
 * <p>验证补偿记录器在异常路径下能够正确序列化事件，不抛出异常。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("OperationLogFallbackLogger 补偿记录器测试")
class OperationLogFallbackLoggerTest {

    @Test
    @DisplayName("完整事件应被序列化记录，不抛出异常")
    void logFullEvent() throws Exception {
        OperationLogFallbackLogger logger = new OperationLogFallbackLogger();

        OperationLogEvent event = OperationLogEvent.builder()
                .module("用户管理")
                .action("创建用户")
                .bizType("USER")
                .bizId("B-001")
                .userId(100L)
                .username("admin")
                .status("SUCCESS")
                .traceId("trace-001")
                .build();
        Throwable err = new RuntimeException("db connection lost");

        // 不应抛异常
        assertThatCode(() -> logger.log(event, err)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null error 字段不应抛出异常")
    void logWithNullError() {
        OperationLogFallbackLogger logger = new OperationLogFallbackLogger();
        OperationLogEvent event = OperationLogEvent.builder()
                .module("X").action("Y").build();

        assertThatCode(() -> logger.log(event, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("内部 Logger 应为名为 audit-fallback 的独立 logger")
    void loggerName() throws Exception {
        Field f = OperationLogFallbackLogger.class.getDeclaredField("FALLBACK_LOGGER");
        f.setAccessible(true);
        Logger logger = (Logger) f.get(null);
        assertThat(logger.getName()).isEqualTo("audit-fallback");
        assertThat(logger).isSameAs(LoggerFactory.getLogger("audit-fallback"));
    }
}
