package com.njydsz.pmis.userinfo.service.impl.auth;

import com.njydsz.pmis.userinfo.entity.user.UserSessionDO;
import com.njydsz.pmis.userinfo.mapper.user.UserSessionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SessionServiceImpl} 单元测试
 *
 * <p>覆盖会话创建、踢出、过期清理、并发会话数控制等核心逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionServiceImpl 会话管理测试")
class SessionServiceImplTest {

    @Mock
    private UserSessionMapper sessionMapper;

    @InjectMocks
    private SessionServiceImpl sessionService;

    private UserSessionDO buildSession(String sessionId, String userId, String status, LocalDateTime loginAt) {
        UserSessionDO s = new UserSessionDO();
        s.setSessionId(sessionId);
        s.setUserId(userId);
        s.setStatus(status);
        s.setLoginAt(loginAt);
        s.setLastActiveAt(loginAt);
        s.setExpireAt(loginAt != null ? loginAt.plusHours(8) : null);
        s.setClientIp("192.168.1.1");
        s.setUserAgent("Mozilla/5.0");
        s.setDeviceType("PC");
        s.setDeleted(0);
        return s;
    }

    @Nested
    @DisplayName("create() 创建会话")
    class CreateTest {

        @Test
        @DisplayName("正常创建会话且并发数未超限")
        void shouldCreateSessionWhenWithinLimit() {
            ReflectionTestUtils.setField(sessionService, "maxConcurrentSessions", 5);
            when(sessionMapper.selectActiveByUserId("U001")).thenReturn(List.of());
            when(sessionMapper.insert(any(UserSessionDO.class))).thenReturn(1);

            UserSessionDO result = sessionService.create("U001", "10.0.0.1", "UA", "PC", 28800);

            assertNotNull(result);
            assertEquals("U001", result.getUserId());
            assertEquals("ACTIVE", result.getStatus());
            assertNotNull(result.getSessionId());
            assertNotNull(result.getExpireAt());
            verify(sessionMapper).insert(any(UserSessionDO.class));
        }

        @Test
        @DisplayName("并发会话超限时踢出最早会话")
        void shouldKickOldestWhenExceedMaxSessions() {
            ReflectionTestUtils.setField(sessionService, "maxConcurrentSessions", 2);
            List<UserSessionDO> active = new ArrayList<>();
            active.add(buildSession("S1", "U001", "ACTIVE", LocalDateTime.now().minusHours(3)));
            active.add(buildSession("S2", "U001", "ACTIVE", LocalDateTime.now().minusHours(2)));
            active.add(buildSession("S3", "U001", "ACTIVE", LocalDateTime.now().minusHours(1)));
            when(sessionMapper.selectActiveByUserId("U001")).thenReturn(active);
            when(sessionMapper.insert(any(UserSessionDO.class))).thenReturn(1);

            sessionService.create("U001", "10.0.0.1", "UA", "PC", 28800);

            // S1 是最早的，应该被踢出
            verify(sessionMapper).updateStatus(eq("S1"), eq("KICKED"), any(LocalDateTime.class), eq("并发会话数超限"));
        }
    }

    @Nested
    @DisplayName("touch() 刷新会话活跃时间")
    class TouchTest {

        @Test
        @DisplayName("活跃会话刷新成功")
        void shouldTouchActiveSession() {
            UserSessionDO s = buildSession("S1", "U001", "ACTIVE", LocalDateTime.now());
            when(sessionMapper.selectBySessionId("S1")).thenReturn(s);

            sessionService.touch("S1");

            assertNotNull(s.getLastActiveAt());
            verify(sessionMapper).updateById(any(UserSessionDO.class));
        }

        @Test
        @DisplayName("不存在的会话不抛异常")
        void shouldNotThrowWhenSessionNotFound() {
            when(sessionMapper.selectBySessionId("INVALID")).thenReturn(null);
            assertDoesNotThrow(() -> sessionService.touch("INVALID"));
        }

        @Test
        @DisplayName("已登出的会话不刷新")
        void shouldNotTouchLoggedOutSession() {
            UserSessionDO s = buildSession("S1", "U001", "LOGOUT", LocalDateTime.now());
            when(sessionMapper.selectBySessionId("S1")).thenReturn(s);

            sessionService.touch("S1");

            verify(sessionMapper, never()).updateById(any(UserSessionDO.class));
        }
    }

    @Nested
    @DisplayName("invalidate() 注销会话")
    class InvalidateTest {

        @Test
        @DisplayName("正常注销会话")
        void shouldInvalidateSession() {
            when(sessionMapper.updateStatus(eq("S1"), eq("LOGOUT"), any(LocalDateTime.class), eq("用户登出")))
                    .thenReturn(1);

            sessionService.invalidate("S1", "用户登出");

            verify(sessionMapper).updateStatus(eq("S1"), eq("LOGOUT"), any(LocalDateTime.class), eq("用户登出"));
        }
    }

    @Nested
    @DisplayName("kickOthers() 踢出其他会话")
    class KickOthersTest {

        @Test
        @DisplayName("踢出除保留会话外的所有活跃会话")
        void shouldKickOthers() {
            when(sessionMapper.kickOtherByUserId("U001", "S_KEEP")).thenReturn(3);

            int kicked = sessionService.kickOthers("U001", "S_KEEP");

            assertEquals(3, kicked);
            verify(sessionMapper).kickOtherByUserId("U001", "S_KEEP");
        }
    }

    @Nested
    @DisplayName("listActive() 查询活跃会话")
    class ListActiveTest {

        @Test
        @DisplayName("返回用户活跃会话列表")
        void shouldReturnActiveSessions() {
            List<UserSessionDO> sessions = List.of(
                    buildSession("S1", "U001", "ACTIVE", LocalDateTime.now()),
                    buildSession("S2", "U001", "ACTIVE", LocalDateTime.now())
            );
            when(sessionMapper.selectActiveByUserId("U001")).thenReturn(sessions);

            List<UserSessionDO> result = sessionService.listActive("U001");

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("无活跃会话返回空列表")
        void shouldReturnEmptyWhenNoActiveSessions() {
            when(sessionMapper.selectActiveByUserId("U001")).thenReturn(List.of());

            List<UserSessionDO> result = sessionService.listActive("U001");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("get() 查询会话")
    class GetTest {

        @Test
        @DisplayName("存在的会话返回实体")
        void shouldReturnSessionWhenExists() {
            UserSessionDO s = buildSession("S1", "U001", "ACTIVE", LocalDateTime.now());
            when(sessionMapper.selectBySessionId("S1")).thenReturn(s);

            UserSessionDO result = sessionService.get("S1");

            assertNotNull(result);
            assertEquals("S1", result.getSessionId());
        }

        @Test
        @DisplayName("不存在的会话返回 null")
        void shouldReturnNullWhenNotExists() {
            when(sessionMapper.selectBySessionId("INVALID")).thenReturn(null);

            UserSessionDO result = sessionService.get("INVALID");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("cleanExpired() 清理过期会话")
    class CleanExpiredTest {

        @Test
        @DisplayName("清理过期活跃会话")
        void shouldCleanExpiredSessions() {
            UserSessionDO expired1 = buildSession("S1", "U001", "ACTIVE",
                    LocalDateTime.now().minusHours(10));
            UserSessionDO expired2 = buildSession("S2", "U002", "ACTIVE",
                    LocalDateTime.now().minusHours(10));
            when(sessionMapper.selectList(any())).thenReturn(List.of(expired1, expired2));

            int cleaned = sessionService.cleanExpired();

            assertEquals(2, cleaned);
            verify(sessionMapper).updateStatus(eq("S1"), eq("EXPIRED"), any(LocalDateTime.class), eq("过期清理"));
            verify(sessionMapper).updateStatus(eq("S2"), eq("EXPIRED"), any(LocalDateTime.class), eq("过期清理"));
        }

        @Test
        @DisplayName("无过期会话返回 0")
        void shouldReturnZeroWhenNoExpired() {
            when(sessionMapper.selectList(any())).thenReturn(List.of());

            int cleaned = sessionService.cleanExpired();

            assertEquals(0, cleaned);
        }
    }

    @Nested
    @DisplayName("enforceMaxSessions() 强制会话数限制")
    class EnforceMaxSessionsTest {

        @Test
        @DisplayName("maxSessions <= 0 时不踢出任何会话")
        void shouldNotKickWhenMaxIsZero() {
            int kicked = sessionService.enforceMaxSessions("U001", 0);

            assertEquals(0, kicked);
            verify(sessionMapper, never()).selectActiveByUserId(any());
        }

        @Test
        @DisplayName("活跃会话数未超限时不踢出")
        void shouldNotKickWhenWithinLimit() {
            when(sessionMapper.selectActiveByUserId("U001")).thenReturn(List.of(
                    buildSession("S1", "U001", "ACTIVE", LocalDateTime.now())
            ));

            int kicked = sessionService.enforceMaxSessions("U001", 5);

            assertEquals(0, kicked);
            verify(sessionMapper, never()).updateStatus(any(), any(), any(), any());
        }

        @Test
        @DisplayName("活跃会话数超限时踢出最早的")
        void shouldKickOldestWhenExceed() {
            List<UserSessionDO> active = new ArrayList<>();
            active.add(buildSession("S3", "U001", "ACTIVE", LocalDateTime.now().minusHours(1)));
            active.add(buildSession("S1", "U001", "ACTIVE", LocalDateTime.now().minusHours(3)));
            active.add(buildSession("S2", "U001", "ACTIVE", LocalDateTime.now().minusHours(2)));
            when(sessionMapper.selectActiveByUserId("U001")).thenReturn(active);

            int kicked = sessionService.enforceMaxSessions("U001", 1);

            assertEquals(2, kicked);
            // S1 是最早的（3小时前），应被先踢出
            verify(sessionMapper).updateStatus(eq("S1"), eq("KICKED"), any(LocalDateTime.class), eq("并发会话数超限"));
            // S2 是第二早的（2小时前），也应被踢出
            verify(sessionMapper).updateStatus(eq("S2"), eq("KICKED"), any(LocalDateTime.class), eq("并发会话数超限"));
            // S3 是最新的，不应被踢出
            verify(sessionMapper, never()).updateStatus(eq("S3"), any(), any(), any());
        }

        @Test
        @DisplayName("loginAt 为 null 的会话排在最前")
        void shouldTreatNullLoginAtAsOldest() {
            List<UserSessionDO> active = new ArrayList<>();
            active.add(buildSession("S2", "U001", "ACTIVE", LocalDateTime.now().minusHours(1)));
            active.add(buildSession("S1", "U001", "ACTIVE", null));
            when(sessionMapper.selectActiveByUserId("U001")).thenReturn(active);

            int kicked = sessionService.enforceMaxSessions("U001", 1);

            assertEquals(1, kicked);
            verify(sessionMapper).updateStatus(eq("S1"), eq("KICKED"), any(LocalDateTime.class), eq("并发会话数超限"));
        }
    }
}
