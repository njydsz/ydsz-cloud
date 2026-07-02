package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.user.entity.UserSessionDO;
import com.njydsz.pmis.user.mapper.UserSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SessionServiceImpl 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SessionServiceImpl 会话管理测试")
class SessionServiceImplTest {

    private UserSessionMapper mapper;
    private SessionServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(UserSessionMapper.class);
        service = new SessionServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 填充基本字段并入库")
    void create() {
        when(mapper.insert(any(UserSessionDO.class))).thenAnswer(inv -> {
            UserSessionDO s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        });
        UserSessionDO s = service.create(7L, "127.0.0.1", "Chrome/120", "WEB", 3600);
        assertThat(s.getUserId()).isEqualTo(7L);
        assertThat(s.getSessionId()).isNotBlank().hasSize(32);
        assertThat(s.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(s.getUserAgent()).isEqualTo("Chrome/120");
        assertThat(s.getDeviceType()).isEqualTo("WEB");
        assertThat(s.getStatus()).isEqualTo("ACTIVE");
        assertThat(s.getExpireAt()).isAfter(LocalDateTime.now());
        verify(mapper, times(1)).insert(any(UserSessionDO.class));
    }

    @Test
    @DisplayName("touch 活跃会话更新最后活跃时间")
    void touch_active() {
        UserSessionDO s = new UserSessionDO();
        s.setSessionId("abc");
        s.setStatus("ACTIVE");
        when(mapper.selectBySessionId("abc")).thenReturn(s);
        service.touch("abc");
        assertThat(s.getLastActiveAt()).isNotNull();
        verify(mapper, times(1)).updateById(any(UserSessionDO.class));
    }

    @Test
    @DisplayName("touch 非 ACTIVE 不更新")
    void touch_inactive() {
        UserSessionDO s = new UserSessionDO();
        s.setSessionId("abc");
        s.setStatus("LOGOUT");
        when(mapper.selectBySessionId("abc")).thenReturn(s);
        service.touch("abc");
        verify(mapper, times(0)).updateById(any(UserSessionDO.class));
    }

    @Test
    @DisplayName("touch 会话不存在直接返回")
    void touch_missing() {
        when(mapper.selectBySessionId("nope")).thenReturn(null);
        service.touch("nope");
        verify(mapper, times(0)).updateById(any(UserSessionDO.class));
    }

    @Test
    @DisplayName("invalidate 委托 mapper 更新状态")
    void invalidate() {
        service.invalidate("sid-1", "用户主动登出");
        ArgumentCaptor<String> reasonCap = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateStatus(eq("sid-1"), eq("LOGOUT"), any(LocalDateTime.class), reasonCap.capture());
        assertThat(reasonCap.getValue()).isEqualTo("用户主动登出");
    }

    @Test
    @DisplayName("kickOthers 强踢同账号其他会话")
    void kickOthers() {
        when(mapper.kickOtherByUserId(1L, "keep")).thenReturn(3);
        int n = service.kickOthers(1L, "keep");
        assertThat(n).isEqualTo(3);
        verify(mapper, times(1)).kickOtherByUserId(1L, "keep");
    }

    @Test
    @DisplayName("listActive 委托 mapper")
    void listActive() {
        when(mapper.selectActiveByUserId(1L)).thenReturn(List.of(new UserSessionDO()));
        assertThat(service.listActive(1L)).hasSize(1);
    }

    @Test
    @DisplayName("get 委托 mapper")
    void get() {
        UserSessionDO s = new UserSessionDO();
        when(mapper.selectBySessionId("sid")).thenReturn(s);
        assertThat(service.get("sid")).isSameAs(s);
    }

    @Test
    @DisplayName("cleanExpired 扫描并批量标记 EXPIRED")
    void cleanExpired_some() {
        UserSessionDO a = new UserSessionDO();
        a.setSessionId("a");
        a.setExpireAt(LocalDateTime.now().minusHours(1));
        a.setStatus("ACTIVE");
        UserSessionDO b = new UserSessionDO();
        b.setSessionId("b");
        b.setExpireAt(LocalDateTime.now().minusDays(1));
        b.setStatus("ACTIVE");
        when(mapper.selectList(any())).thenReturn(List.of(a, b));

        int n = service.cleanExpired();
        assertThat(n).isEqualTo(2);
        verify(mapper, times(2)).updateStatus(any(), eq("EXPIRED"), any(LocalDateTime.class), any());
    }

    @Test
    @DisplayName("cleanExpired 无过期记录返回 0")
    void cleanExpired_empty() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertThat(service.cleanExpired()).isZero();
        verify(mapper, times(0)).updateStatus(any(), any(), any(), any());
    }
}
