package com.njydsz.pmis.audit.listener;

import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.fallback.OperationLogFallbackLogger;
import com.njydsz.pmis.audit.mapper.OperationLogMapper;
import com.njydsz.pmis.common.event.OperationLogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("OperationLogListener 落库监听器测试")
class OperationLogListenerTest {

    private OperationLogMapper mapper;
    private OperationLogFallbackLogger fallbackLogger;
    private OperationLogListener listener;

    @BeforeEach
    void setUp() {
        mapper = mock(OperationLogMapper.class);
        fallbackLogger = mock(OperationLogFallbackLogger.class);
        listener = new OperationLogListener(mapper, fallbackLogger);
    }

    @Test
    @DisplayName("监听事件应转换为 DO 并落库")
    void onEvent() {
        OperationLogEvent e = OperationLogEvent.builder()
                .module("用户管理")
                .action("创建用户")
                .bizType("USER")
                .bizId("B-001")
                .userId(100L)
                .username("admin")
                .requestUrl("/api/v1/user")
                .httpMethod("POST")
                .clientIp("127.0.0.1")
                .paramsJson("{\"username\":\"a\"}")
                .status("SUCCESS")
                .costMs(50L)
                .traceId("trace-1")
                .tenantId(1L)
                .build();
        listener.onOperationLog(e);

        ArgumentCaptor<OperationLogDO> captor = ArgumentCaptor.forClass(OperationLogDO.class);
        verify(mapper).insertLog(captor.capture());
        OperationLogDO l = captor.getValue();
        assertThat(l.getModule()).isEqualTo("用户管理");
        assertThat(l.getAction()).isEqualTo("创建用户");
        assertThat(l.getBizType()).isEqualTo("USER");
        assertThat(l.getUserId()).isEqualTo(100L);
        assertThat(l.getStatus()).isEqualTo("SUCCESS");
        assertThat(l.getCreatedAt()).isNotNull();
        // 成功路径不应触发 fallback
        verify(fallbackLogger, never()).log(any(), any());
    }

    @Test
    @DisplayName("第 1 次失败 + 重试成功：不写入 fallback")
    void retrySuccess_noFallback() {
        OperationLogEvent e = OperationLogEvent.builder().module("X").action("Y").build();
        // insertLog 返回 int（非 void），用 doReturn(1) 代替 doNothing()
        doThrow(new RuntimeException("db down"))
                .doReturn(1)
                .when(mapper).insertLog(any());

        listener.onOperationLog(e);

        // 应该被调用 2 次（1 次失败 + 1 次重试）
        verify(mapper, times(2)).insertLog(any());
        verify(fallbackLogger, never()).log(any(), any());
    }

    @Test
    @DisplayName("重试仍失败：写入 fallback log")
    void retryFailed_fallbackWritten() {
        OperationLogEvent e = OperationLogEvent.builder()
                .module("M").action("A").bizType("T").bizId("B-1")
                .userId(1L).username("u").status("SUCCESS")
                .traceId("trace-x").build();
        // 第 1 次和第 2 次都抛异常
        doThrow(new RuntimeException("db down 1"))
                .doThrow(new RuntimeException("db down 2"))
                .when(mapper).insertLog(any());

        listener.onOperationLog(e);

        // 应该被调用 2 次
        verify(mapper, times(2)).insertLog(any());
        // 应该调用 fallback
        ArgumentCaptor<Throwable> errCap = ArgumentCaptor.forClass(Throwable.class);
        verify(fallbackLogger).log(eq(e), errCap.capture());
        assertThat(errCap.getValue().getMessage()).isEqualTo("db down 1");
    }

    @Test
    @DisplayName("fallback 调用不应抛出异常")
    void fallbackSafe() {
        OperationLogEvent e = OperationLogEvent.builder().module("X").action("Y").build();
        doThrow(new RuntimeException("db down"))
                .doThrow(new RuntimeException("db down again"))
                .when(mapper).insertLog(any());
        // fallback 自身抛异常也不应影响监听器
        doThrow(new RuntimeException("fallback io err"))
                .when(fallbackLogger).log(any(), any());

        // 不抛异常
        listener.onOperationLog(e);
    }
}
