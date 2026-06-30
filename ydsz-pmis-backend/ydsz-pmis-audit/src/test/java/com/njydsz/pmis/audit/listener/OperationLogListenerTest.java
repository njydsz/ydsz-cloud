package com.njydsz.pmis.audit.listener;

import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.mapper.OperationLogMapper;
import com.njydsz.pmis.common.event.OperationLogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("OperationLogListener 落库监听器测试")
class OperationLogListenerTest {

    private OperationLogMapper mapper;
    private OperationLogListener listener;

    @BeforeEach
    void setUp() {
        mapper = mock(OperationLogMapper.class);
        listener = new OperationLogListener(mapper);
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
    }

    @Test
    @DisplayName("落库异常不应向上抛出")
    void swallowException() {
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(mapper).insertLog(any());
        OperationLogEvent e = OperationLogEvent.builder().module("X").action("Y").build();
        // 不会抛异常
        listener.onOperationLog(e);
    }
}
