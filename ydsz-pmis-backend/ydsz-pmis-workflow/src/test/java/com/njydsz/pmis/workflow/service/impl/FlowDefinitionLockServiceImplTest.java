package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.engine.BpmnXmlParser;
import com.njydsz.pmis.workflow.engine.FlowDefinitionCacheService;
import com.njydsz.pmis.workflow.engine.FlowGraphValidator;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link FlowDefinitionServiceImpl} P2-4 设计器协同编辑锁定 单元测试。
 *
 * <p>覆盖 lockDefinition / unlockDefinition / getLockStatus 三个方法的全部场景：
 * <ul>
 *   <li>加锁：未锁定 / 同号续约 / 他人持锁未超时 / 他人持锁已超时 / 定义不存在 / 参数为空 / 并发冲突重试</li>
 *   <li>解锁：持锁人本人 / 他人持锁 / 未锁定（幂等） / 定义不存在 / 并发已解锁</li>
 *   <li>查询状态：未锁定 / 已锁定未超时 / 已锁定已超时 / 定义不存在</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P2-4: 设计器协同编辑锁定 - FlowDefinitionServiceImpl")
class FlowDefinitionLockServiceImplTest {

    @Mock
    private FlowDefinitionMapper definitionMapper;
    @Mock
    private FlowNodeMapper nodeMapper;
    @Mock
    private FlowSkipMapper skipMapper;
    @Mock
    private BpmnXmlParser bpmnXmlParser;
    @Mock
    private FlowGraphValidator graphValidator;
    @Mock
    private FlowDefinitionCacheService flowDefinitionCacheService;

    @InjectMocks
    private FlowDefinitionServiceImpl service;

    /** P2-4 测试锁定超时阈值（5 分钟） */
    private static final long LOCK_TIMEOUT_MINUTES = 5L;

    @BeforeEach
    void setUp() {
        // 通过反射注入 @Value 字段（@InjectMocks 不会自动注入 @Value）
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", LOCK_TIMEOUT_MINUTES);
    }

    private FlowDefinitionDO buildDefinition(String id, String lockedBy, LocalDateTime lockedAt, Integer version) {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(id);
        def.setFlowCode("test_flow");
        def.setFlowName("测试流程");
        def.setLockedBy(lockedBy);
        def.setLockedAt(lockedAt);
        def.setVersion(version == null ? 0 : version);
        def.setDeleted(0);
        return def;
    }

    // ============================== 加锁测试 ==============================

    @Nested
    @DisplayName("lockDefinition 加锁测试")
    class LockDefinitionTest {

        @Test
        @DisplayName("参数 definitionId 为空 → BAD_REQUEST")
        void shouldThrowWhenDefinitionIdEmpty() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.lockDefinition("", "user1"));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_d6e7f8a9", ex.getErrorMessage());
            verifyNoInteractions(definitionMapper);
        }

        @Test
        @DisplayName("参数 userId 为空 → BAD_REQUEST")
        void shouldThrowWhenUserIdEmpty() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.lockDefinition("def1", ""));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_d6e7f8a9", ex.getErrorMessage());
            verifyNoInteractions(definitionMapper);
        }

        @Test
        @DisplayName("流程定义不存在 → NOT_FOUND")
        void shouldThrowWhenDefinitionNotFound() {
            when(definitionMapper.selectById("def1")).thenReturn(null);
            BizException ex = assertThrows(BizException.class,
                    () -> service.lockDefinition("def1", "user1"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_e7f8a9b0", ex.getErrorMessage());
        }

        @Test
        @DisplayName("流程定义已被软删除 → NOT_FOUND")
        void shouldThrowWhenDefinitionDeleted() {
            FlowDefinitionDO def = buildDefinition("def1", null, null, 0);
            def.setDeleted(1);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            BizException ex = assertThrows(BizException.class,
                    () -> service.lockDefinition("def1", "user1"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("未锁定 → CAS 加锁成功")
        void shouldLockWhenUnlocked() {
            FlowDefinitionDO def = buildDefinition("def1", null, null, 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            when(definitionMapper.casLock(eq("def1"), eq("user1"), any(LocalDateTime.class),
                    eq("user1"), any(LocalDateTime.class), eq(0)))
                    .thenReturn(1);

            boolean result = service.lockDefinition("def1", "user1");

            assertTrue(result);
            verify(definitionMapper, times(1)).casLock(eq("def1"), eq("user1"),
                    any(LocalDateTime.class), eq("user1"), any(LocalDateTime.class), eq(0));
        }

        @Test
        @DisplayName("同一人持锁 → CAS 续约成功")
        void shouldRenewWhenSameUser() {
            FlowDefinitionDO def = buildDefinition("def1", "user1",
                    LocalDateTime.now().minusMinutes(2), 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            // expectedOldBy = user1 → SQL 条件 locked_by = user1 命中续约
            when(definitionMapper.casLock(eq("def1"), eq("user1"), any(LocalDateTime.class),
                    eq("user1"), any(LocalDateTime.class), eq(0)))
                    .thenReturn(1);

            boolean result = service.lockDefinition("def1", "user1");

            assertTrue(result);
        }

        @Test
        @DisplayName("他人持锁未超时 → CAS 失败 → 抛 CONFLICT")
        void shouldThrowWhenLockedByOtherNotExpired() {
            // 持锁 2 分钟，超时阈值 5 分钟，未超时
            FlowDefinitionDO def = buildDefinition("def1", "user2",
                    LocalDateTime.now().minusMinutes(2), 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            when(definitionMapper.casLock(any(), any(), any(), any(), any(), any()))
                    .thenReturn(0);
            // 重查时仍是 user2 持锁且未超时
            FlowDefinitionDO latest = buildDefinition("def1", "user2",
                    LocalDateTime.now().minusMinutes(2), 1);
            when(definitionMapper.selectById("def1")).thenReturn(def, latest);

            BizException ex = assertThrows(BizException.class,
                    () -> service.lockDefinition("def1", "user1"));
            assertEquals(BizErrorCode.RESOURCE_CONFLICT.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_f8a9b0c1", ex.getErrorMessage());
        }

        @Test
        @DisplayName("他人持锁已超时 → CAS 重试抢占成功")
        void shouldPreemptWhenExpired() {
            // 持锁 10 分钟，超时阈值 5 分钟，已超时
            LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(10);
            FlowDefinitionDO def = buildDefinition("def1", "user2", expiredAt, 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            // 首次 CAS 失败（version 不匹配）
            when(definitionMapper.casLock(eq("def1"), eq("user1"), any(LocalDateTime.class),
                    eq("user1"), any(LocalDateTime.class), eq(0)))
                    .thenReturn(0);
            // 重查时仍是 user2 持锁，但已超时，version 变为 1
            FlowDefinitionDO latest = buildDefinition("def1", "user2", expiredAt, 1);
            when(definitionMapper.selectById("def1")).thenReturn(def, latest);
            // 重试 CAS 成功
            when(definitionMapper.casLock(eq("def1"), eq("user1"), any(LocalDateTime.class),
                    eq("user1"), any(LocalDateTime.class), eq(1)))
                    .thenReturn(1);

            boolean result = service.lockDefinition("def1", "user1");

            assertTrue(result);
        }

        @Test
        @DisplayName("他人持锁已超时 → CAS 重试仍失败 → 抛 CONFLICT")
        void shouldThrowWhenRetryFails() {
            LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(10);
            FlowDefinitionDO def = buildDefinition("def1", "user2", expiredAt, 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            when(definitionMapper.casLock(any(), any(), any(), any(), any(), any()))
                    .thenReturn(0);  // 两次都失败
            FlowDefinitionDO latest = buildDefinition("def1", "user2", expiredAt, 1);
            when(definitionMapper.selectById("def1")).thenReturn(def, latest);

            BizException ex = assertThrows(BizException.class,
                    () -> service.lockDefinition("def1", "user1"));
            assertEquals(BizErrorCode.RESOURCE_CONFLICT.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_f8a9b0c1", ex.getErrorMessage());
        }

        @Test
        @DisplayName("CAS 失败但锁已被并发清空 → 抛并发冲突")
        void shouldThrowConcurrentConflictWhenLockCleared() {
            FlowDefinitionDO def = buildDefinition("def1", "user1",
                    LocalDateTime.now().minusMinutes(1), 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            when(definitionMapper.casLock(any(), any(), any(), any(), any(), any()))
                    .thenReturn(0);
            // 重查时锁已被并发清空（lockedBy=null），不进入他人持锁分支
            FlowDefinitionDO latest = buildDefinition("def1", null, null, 1);
            when(definitionMapper.selectById("def1")).thenReturn(def, latest);

            BizException ex = assertThrows(BizException.class,
                    () -> service.lockDefinition("def1", "user1"));
            assertEquals(BizErrorCode.RESOURCE_CONFLICT.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_a9b0c1d2", ex.getErrorMessage());
        }
    }

    // ============================== 解锁测试 ==============================

    @Nested
    @DisplayName("unlockDefinition 解锁测试")
    class UnlockDefinitionTest {

        @Test
        @DisplayName("参数为空 → BAD_REQUEST")
        void shouldThrowWhenParamEmpty() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.unlockDefinition("", "user1"));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("流程定义不存在 → NOT_FOUND")
        void shouldThrowWhenDefinitionNotFound() {
            when(definitionMapper.selectById("def1")).thenReturn(null);
            BizException ex = assertThrows(BizException.class,
                    () -> service.unlockDefinition("def1", "user1"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("未锁定 → 幂等返回 true")
        void shouldReturnTrueWhenUnlocked() {
            FlowDefinitionDO def = buildDefinition("def1", null, null, 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);

            boolean result = service.unlockDefinition("def1", "user1");

            assertTrue(result);
            verify(definitionMapper, never()).casUnlock(any(), any(), any());
        }

        @Test
        @DisplayName("持锁人本人解锁 → CAS 成功")
        void shouldUnlockWhenOwner() {
            FlowDefinitionDO def = buildDefinition("def1", "user1",
                    LocalDateTime.now(), 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            when(definitionMapper.casUnlock("def1", "user1", 0)).thenReturn(1);

            boolean result = service.unlockDefinition("def1", "user1");

            assertTrue(result);
        }

        @Test
        @DisplayName("他人尝试解锁 → 抛 FORBIDDEN")
        void shouldThrowWhenUnlockOthers() {
            FlowDefinitionDO def = buildDefinition("def1", "user2",
                    LocalDateTime.now(), 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            when(definitionMapper.casUnlock("def1", "user1", 0)).thenReturn(0);
            // 重查时仍是 user2 持锁
            FlowDefinitionDO latest = buildDefinition("def1", "user2",
                    LocalDateTime.now(), 1);
            when(definitionMapper.selectById("def1")).thenReturn(def, latest);

            BizException ex = assertThrows(BizException.class,
                    () -> service.unlockDefinition("def1", "user1"));
            assertEquals(BizErrorCode.FORBIDDEN.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_b1c2d3e4", ex.getErrorMessage());
        }

        @Test
        @DisplayName("CAS 失败但锁已被并发清空 → 视为成功")
        void shouldReturnTrueWhenConcurrentCleared() {
            FlowDefinitionDO def = buildDefinition("def1", "user1",
                    LocalDateTime.now(), 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            when(definitionMapper.casUnlock("def1", "user1", 0)).thenReturn(0);
            // 重查时锁已被并发清空（lockedBy=null）
            FlowDefinitionDO latest = buildDefinition("def1", null, null, 1);
            when(definitionMapper.selectById("def1")).thenReturn(def, latest);

            boolean result = service.unlockDefinition("def1", "user1");

            assertTrue(result);
        }
    }

    // ============================== 查询锁状态测试 ==============================

    @Nested
    @DisplayName("getLockStatus 查询锁状态测试")
    class GetLockStatusTest {

        @Test
        @DisplayName("参数为空 → BAD_REQUEST")
        void shouldThrowWhenParamEmpty() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.getLockStatus(""));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("流程定义不存在 → 返回 null")
        void shouldReturnNullWhenNotFound() {
            when(definitionMapper.selectById("def1")).thenReturn(null);
            Map<String, Object> result = service.getLockStatus("def1");
            assertNull(result);
        }

        @Test
        @DisplayName("流程定义已软删除 → 返回 null")
        void shouldReturnNullWhenDeleted() {
            FlowDefinitionDO def = buildDefinition("def1", null, null, 0);
            def.setDeleted(1);
            when(definitionMapper.selectById("def1")).thenReturn(def);
            Map<String, Object> result = service.getLockStatus("def1");
            assertNull(result);
        }

        @Test
        @DisplayName("未锁定 → locked=false")
        void shouldReturnUnlocked() {
            FlowDefinitionDO def = buildDefinition("def1", null, null, 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);

            Map<String, Object> result = service.getLockStatus("def1");

            assertEquals(false, result.get("locked"));
            assertNull(result.get("lockedBy"));
            assertNull(result.get("lockedAt"));
            assertEquals(false, result.get("expired"));
        }

        @Test
        @DisplayName("已锁定未超时 → locked=true, expired=false")
        void shouldReturnLockedNotExpired() {
            FlowDefinitionDO def = buildDefinition("def1", "user1",
                    LocalDateTime.now().minusMinutes(2), 0);
            when(definitionMapper.selectById("def1")).thenReturn(def);

            Map<String, Object> result = service.getLockStatus("def1");

            assertEquals(true, result.get("locked"));
            assertEquals("user1", result.get("lockedBy"));
            assertNotNull(result.get("lockedAt"));
            assertEquals(false, result.get("expired"));
        }

        @Test
        @DisplayName("已锁定已超时 → locked=true, expired=true")
        void shouldReturnLockedExpired() {
            FlowDefinitionDO def = buildDefinition("def1", "user1",
                    LocalDateTime.now().minusMinutes(10), 0);  // 超时阈值 5 分钟
            when(definitionMapper.selectById("def1")).thenReturn(def);

            Map<String, Object> result = service.getLockStatus("def1");

            assertEquals(true, result.get("locked"));
            assertEquals("user1", result.get("lockedBy"));
            assertNotNull(result.get("lockedAt"));
            assertEquals(true, result.get("expired"));
        }
    }
}
