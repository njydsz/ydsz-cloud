package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.feign.NotificationPushClient;
import com.njydsz.pmis.project.dto.AlertDispatchDTO;
import com.njydsz.pmis.project.entity.AlertDispatchDO;
import com.njydsz.pmis.project.feign.MessageServiceClient;
import com.njydsz.pmis.project.mapper.AlertDispatchMapper;
import com.njydsz.pmis.project.service.AlertDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 预警分级推送服务单元测试
 *
 * <p>P1-4 修复重点：验证 retryFailed 通过 self 代理调用 dispatchNow，
 * 避免 Spring AOP 自调用陷阱导致 @GlobalTransactional 失效。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>submit - 基本创建 / 参数校验 / 默认值兜底 / 幂等更新</li>
 *   <li>dispatchNow - 已发送跳过 / 单通道成功 / 多通道部分失败 / Feign 异常降级</li>
 *   <li>retryFailed - self 代理调用 / 单条异常隔离</li>
 *   <li>resolveTargetRoles - 各等级角色映射</li>
 *   <li>cancel - 标记取消 / 已 SENT 不可取消</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("预警分级推送服务测试")
class AlertDispatchServiceImplTest {

    @Mock
    private AlertDispatchMapper mapper;

    @Mock
    private MessageServiceClient messageClient;

    @Mock
    private NotificationPushClient pushClient;

    /** self 代理 mock，模拟 Spring AOP 注入的自身引用 */
    @Mock
    private AlertDispatchService self;

    @InjectMocks
    private AlertDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        // 注入 @Autowired @Lazy 字段（Mockito @InjectMocks 不会自动处理）
        ReflectionTestUtils.setField(service, "self", self);
    }

    // =========================================================================
    //  submit - 提交预警
    // =========================================================================

    @Test
    @DisplayName("submit - 基本创建应填充默认值并落库")
    void submit_shouldInsertWithDefaults() {
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("BUDGET");
        dto.setAlertLevel("RED");
        dto.setTitle("预算超支预警");
        dto.setContent("项目 X 预算超支 20%");
        dto.setSourceType("project");
        dto.setSourceId("P-001");

        // mapper.selectList 用于 findByCode 幂等检查
        when(mapper.selectList(any())).thenReturn(List.of());
        when(mapper.insert(any(AlertDispatchDO.class))).thenAnswer(inv -> {
            ((AlertDispatchDO) inv.getArgument(0)).setId(100L);
            return 1;
        });

        Long id = service.submit(dto);

        assertEquals(100L, id);
        verify(mapper).insert(argThat((AlertDispatchDO d) ->
                "RED".equals(d.getAlertLevel())
                        && d.getAlertCode() != null && !d.getAlertCode().isEmpty()
                        && "IN_APP,EMAIL".equals(d.getPushChannels())
                        && "PMO,GM,CFO".equals(d.getTargetRole())
                        && "PENDING".equals(d.getStatus())
                        && Integer.valueOf(0).equals(d.getRetryCount())
                        && d.getTenantId() != null
                        && d.getDispatchedAt() != null
                        && "".equals(d.getProviderTraceId())));
    }

    @Test
    @DisplayName("submit - YELLOW 等级默认仅 IN_APP 通道、PM+PMO 角色")
    void submit_yellowLevelDefaults() {
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("RISK");
        dto.setAlertLevel("yellow"); // 测试大小写归一
        dto.setTitle("风险预警");

        when(mapper.selectList(any())).thenReturn(List.of());
        when(mapper.insert(any(AlertDispatchDO.class))).thenAnswer(inv -> {
            ((AlertDispatchDO) inv.getArgument(0)).setId(1L);
            return 1;
        });

        service.submit(dto);

        verify(mapper).insert(argThat((AlertDispatchDO d) ->
                "YELLOW".equals(d.getAlertLevel())
                        && "IN_APP".equals(d.getPushChannels())
                        && "PM,PMO".equals(d.getTargetRole())));
    }

    @Test
    @DisplayName("submit - 显式字段应保留不被覆盖")
    void submit_shouldKeepExplicitFields() {
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("EVM");
        dto.setAlertLevel("RED");
        dto.setTitle("EVM 预警");
        dto.setAlertCode("ALERT-CUSTOM-001");
        dto.setPushChannels("SMS");
        dto.setTargetRole("GM");

        when(mapper.selectList(any())).thenReturn(List.of());
        when(mapper.insert(any(AlertDispatchDO.class))).thenAnswer(inv -> {
            ((AlertDispatchDO) inv.getArgument(0)).setId(1L);
            return 1;
        });

        service.submit(dto);

        verify(mapper).insert(argThat((AlertDispatchDO d) ->
                "ALERT-CUSTOM-001".equals(d.getAlertCode())
                        && "SMS".equals(d.getPushChannels())
                        && "GM".equals(d.getTargetRole())));
    }

    @Test
    @DisplayName("submit - 相同 alertCode 应幂等更新而非插入")
    void submit_idempotentUpdateWhenCodeExists() {
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("BUDGET");
        dto.setAlertLevel("RED");
        dto.setTitle("预算预警");
        dto.setAlertCode("ALERT-DUP-001");

        AlertDispatchDO existing = new AlertDispatchDO();
        existing.setId(99L);
        existing.setAlertCode("ALERT-DUP-001");

        when(mapper.selectList(any())).thenReturn(List.of(existing));

        Long id = service.submit(dto);

        assertEquals(99L, id);
        verify(mapper).updateById(argThat((AlertDispatchDO d) ->
                d.getId() == 99L && "RED".equals(d.getAlertLevel())));
        verify(mapper, never()).insert(any(AlertDispatchDO.class));
    }

    @Test
    @DisplayName("submit - dto 为 null 应抛 BAD_REQUEST")
    void submit_nullDtoThrows() {
        BizException ex = assertThrows(BizException.class, () -> service.submit(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("submit - alertType 为空应抛 BAD_REQUEST")
    void submit_emptyAlertTypeThrows() {
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertLevel("RED");
        dto.setTitle("t");
        BizException ex = assertThrows(BizException.class, () -> service.submit(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("submit - title 为空应抛 BAD_REQUEST")
    void submit_emptyTitleThrows() {
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("BUDGET");
        dto.setAlertLevel("RED");
        BizException ex = assertThrows(BizException.class, () -> service.submit(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("submit - 非法 alertLevel 应抛 BAD_REQUEST")
    void submit_invalidLevelThrows() {
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("BUDGET");
        dto.setAlertLevel("PURPLE");
        dto.setTitle("t");
        BizException ex = assertThrows(BizException.class, () -> service.submit(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // =========================================================================
    //  dispatchNow - 立即分发
    // =========================================================================

    @Test
    @DisplayName("dispatchNow - id 为 null 应抛 BAD_REQUEST")
    void dispatchNow_nullIdThrows() {
        BizException ex = assertThrows(BizException.class, () -> service.dispatchNow(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("dispatchNow - 记录不存在应抛 NOT_FOUND")
    void dispatchNow_notFoundThrows() {
        when(mapper.selectById(404L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.dispatchNow(404L));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("dispatchNow - 已 SENT 应直接返回 true 不再分发")
    void dispatchNow_alreadySentSkips() {
        AlertDispatchDO d = buildDO(1L, "SENT");
        when(mapper.selectById(1L)).thenReturn(d);

        boolean ok = service.dispatchNow(1L);

        assertTrue(ok);
        verify(messageClient, never()).send(any());
        verify(mapper, never()).markSent(anyLong(), any());
    }

    @Test
    @DisplayName("dispatchNow - 已 CANCELLED 应直接返回 true 不再分发")
    void dispatchNow_cancelledSkips() {
        AlertDispatchDO d = buildDO(1L, "CANCELLED");
        when(mapper.selectById(1L)).thenReturn(d);

        boolean ok = service.dispatchNow(1L);

        assertTrue(ok);
        verify(messageClient, never()).send(any());
    }

    @Test
    @DisplayName("dispatchNow - 单通道成功应标记 SENT")
    void dispatchNow_singleChannelSuccess() {
        AlertDispatchDO d = buildDO(1L, "PENDING");
        d.setPushChannels("IN_APP");
        when(mapper.selectById(1L)).thenReturn(d);
        when(messageClient.send(any(MessageRequest.class)))
                .thenReturn(Result.ok(MessageResult.ok("IN_APP", "trace-001")));
        when(mapper.markSent(eq(1L), any())).thenReturn(1);

        boolean ok = service.dispatchNow(1L);

        assertTrue(ok);
        verify(mapper).markSent(eq(1L), any());
        verify(mapper, never()).markFailed(anyLong(), anyString());
        // 实时推送会被调用一次（broadcast），失败不影响主流程
        verify(pushClient).broadcast(eq("ALERT"), any());
    }

    @Test
    @DisplayName("dispatchNow - 多通道全部成功应标记 SENT")
    void dispatchNow_multiChannelAllSuccess() {
        AlertDispatchDO d = buildDO(1L, "PENDING");
        d.setPushChannels("IN_APP,EMAIL");
        when(mapper.selectById(1L)).thenReturn(d);
        when(messageClient.send(any()))
                .thenReturn(Result.ok(MessageResult.ok("IN_APP", "t1")))
                .thenReturn(Result.ok(MessageResult.ok("EMAIL", "t2")));
        when(mapper.markSent(anyLong(), any())).thenReturn(1);

        boolean ok = service.dispatchNow(1L);

        assertTrue(ok);
        verify(messageClient, times(2)).send(any());
        verify(mapper).markSent(eq(1L), any());
    }

    @Test
    @DisplayName("dispatchNow - 部分通道失败应标记 FAILED")
    void dispatchNow_partialChannelFailure() {
        AlertDispatchDO d = buildDO(1L, "PENDING");
        d.setPushChannels("IN_APP,EMAIL");
        when(mapper.selectById(1L)).thenReturn(d);
        when(messageClient.send(any()))
                .thenReturn(Result.ok(MessageResult.ok("IN_APP", "t1")))
                .thenReturn(Result.ok(MessageResult.fail("EMAIL", "SMTP 拒绝")));
        when(mapper.markFailed(anyLong(), anyString())).thenReturn(1);

        boolean ok = service.dispatchNow(1L);

        assertFalse(ok);
        verify(mapper).markFailed(eq(1L), contains("SMTP 拒绝"));
        verify(mapper, never()).markSent(anyLong(), any());
    }

    @Test
    @DisplayName("dispatchNow - Feign 抛异常应降级标记 FAILED 而非向上抛")
    void dispatchNow_feignExceptionDegradesGracefully() {
        AlertDispatchDO d = buildDO(1L, "PENDING");
        d.setPushChannels("IN_APP");
        when(mapper.selectById(1L)).thenReturn(d);
        when(messageClient.send(any())).thenThrow(new RuntimeException("连接超时"));
        when(mapper.markFailed(anyLong(), anyString())).thenReturn(1);

        boolean ok = service.dispatchNow(1L);

        assertFalse(ok);
        verify(mapper).markFailed(eq(1L), contains("连接超时"));
    }

    @Test
    @DisplayName("dispatchNow - messageClient 为 null 应返回占位成功（无 Feign 环境）")
    void dispatchNow_noFeignEnvReturnsOk() {
        AlertDispatchDO d = buildDO(1L, "PENDING");
        d.setPushChannels("IN_APP");
        when(mapper.selectById(1L)).thenReturn(d);
        // 通过反射将 messageClient 设为 null（模拟无 Feign 环境）
        ReflectionTestUtils.setField(service, "messageClient", null);
        when(mapper.markSent(anyLong(), any())).thenReturn(1);

        boolean ok = service.dispatchNow(1L);

        assertTrue(ok);
        verify(mapper).markSent(eq(1L), any());
    }

    // =========================================================================
    //  retryFailed - 重试失败预警（P1-4: self 代理调用）
    // =========================================================================

    @Test
    @DisplayName("retryFailed - 应通过 self 代理调用 dispatchNow（激活 @GlobalTransactional）")
    void retryFailed_shouldCallDispatchNowViaSelfProxy() {
        AlertDispatchDO d1 = buildDO(1L, "FAILED");
        d1.setPushChannels("IN_APP");
        AlertDispatchDO d2 = buildDO(2L, "FAILED");
        d2.setPushChannels("IN_APP");
        when(mapper.selectRetryable(any(), eq(3))).thenReturn(List.of(d1, d2));
        when(mapper.incrementRetry(anyLong())).thenReturn(1);
        // self 代理返回 true（模拟 dispatchNow 成功）
        when(self.dispatchNow(1L)).thenReturn(true);
        when(self.dispatchNow(2L)).thenReturn(true);

        int n = service.retryFailed(3);

        assertEquals(2, n);
        // P1-4 核心断言：必须通过 self 调用，而非 this
        verify(self).dispatchNow(1L);
        verify(self).dispatchNow(2L);
        verify(mapper).incrementRetry(1L);
        verify(mapper).incrementRetry(2L);
    }

    @Test
    @DisplayName("retryFailed - maxRetry <= 0 应默认为 3")
    void retryFailed_defaultMaxRetry() {
        when(mapper.selectRetryable(any(), eq(3))).thenReturn(List.of());

        service.retryFailed(0);

        verify(mapper).selectRetryable(any(), eq(3));
    }

    @Test
    @DisplayName("retryFailed - 单条异常应隔离不影响其他条目")
    void retryFailed_singleFailureIsolated() {
        AlertDispatchDO d1 = buildDO(1L, "FAILED");
        AlertDispatchDO d2 = buildDO(2L, "FAILED");
        when(mapper.selectRetryable(any(), eq(3))).thenReturn(List.of(d1, d2));
        when(mapper.incrementRetry(anyLong())).thenReturn(1);
        // 第一条抛异常，第二条正常
        when(self.dispatchNow(1L)).thenThrow(new RuntimeException("DB 故障"));
        when(self.dispatchNow(2L)).thenReturn(true);

        int n = service.retryFailed(3);

        assertEquals(1, n);
        verify(self).dispatchNow(1L);
        verify(self).dispatchNow(2L);
    }

    @Test
    @DisplayName("retryFailed - dispatchNow 返回 false 不应计入成功数")
    void retryFailed_failedDispatchNotCounted() {
        AlertDispatchDO d1 = buildDO(1L, "FAILED");
        when(mapper.selectRetryable(any(), eq(3))).thenReturn(List.of(d1));
        when(mapper.incrementRetry(anyLong())).thenReturn(1);
        when(self.dispatchNow(1L)).thenReturn(false);

        int n = service.retryFailed(3);

        assertEquals(0, n);
    }

    // =========================================================================
    //  resolveTargetRoles - 等级 → 角色映射
    // =========================================================================

    @Test
    @DisplayName("resolveTargetRoles - RED → PMO+GM+CFO")
    void resolveTargetRoles_red() {
        List<String> roles = service.resolveTargetRoles("RED");
        assertEquals(List.of("PMO", "GM", "CFO"), roles);
    }

    @Test
    @DisplayName("resolveTargetRoles - YELLOW → PM+PMO")
    void resolveTargetRoles_yellow() {
        List<String> roles = service.resolveTargetRoles("YELLOW");
        assertEquals(List.of("PM", "PMO"), roles);
    }

    @Test
    @DisplayName("resolveTargetRoles - NORMAL → PM")
    void resolveTargetRoles_normal() {
        List<String> roles = service.resolveTargetRoles("NORMAL");
        assertEquals(List.of("PM"), roles);
    }

    @Test
    @DisplayName("resolveTargetRoles - 大小写不敏感 / null 返回空列表")
    void resolveTargetRoles_caseInsensitiveAndNull() {
        assertEquals(List.of("PMO", "GM", "CFO"), service.resolveTargetRoles("red"));
        assertEquals(List.of("PM", "PMO"), service.resolveTargetRoles("Yellow"));
        assertTrue(service.resolveTargetRoles(null).isEmpty());
        assertTrue(service.resolveTargetRoles("UNKNOWN").isEmpty());
    }

    // =========================================================================
    //  cancel - 取消预警
    // =========================================================================

    @Test
    @DisplayName("cancel - PENDING 可取消并写 failReason")
    void cancel_pendingSucceeds() {
        AlertDispatchDO d = buildDO(1L, "PENDING");
        when(mapper.selectById(1L)).thenReturn(d);
        when(mapper.updateById(any(AlertDispatchDO.class))).thenReturn(1);

        service.cancel(1L, "误报");

        verify(mapper).updateById(argThat((AlertDispatchDO u) ->
                "CANCELLED".equals(u.getStatus())
                        && "误报".equals(u.getFailReason())
                        && u.getUpdatedAt() != null));
    }

    @Test
    @DisplayName("cancel - id 为 null 应抛 BAD_REQUEST")
    void cancel_nullIdThrows() {
        BizException ex = assertThrows(BizException.class, () -> service.cancel(null, "r"));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("cancel - 记录不存在应抛 NOT_FOUND")
    void cancel_notFoundThrows() {
        when(mapper.selectById(404L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.cancel(404L, "r"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("cancel - 已 SENT 不可取消应抛 BAD_REQUEST")
    void cancel_sentThrows() {
        AlertDispatchDO d = buildDO(1L, "SENT");
        when(mapper.selectById(1L)).thenReturn(d);

        BizException ex = assertThrows(BizException.class, () -> service.cancel(1L, "r"));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any(AlertDispatchDO.class));
    }

    // =========================================================================
    //  查询方法
    // =========================================================================

    @Test
    @DisplayName("listByLevelAndStatus - 透传到 mapper")
    void listByLevelAndStatus_passThrough() {
        AlertDispatchDO d = buildDO(1L, "SENT");
        when(mapper.selectByLevelAndStatus("RED", "SENT")).thenReturn(List.of(d));

        List<AlertDispatchDO> result = service.listByLevelAndStatus("RED", "SENT");

        assertEquals(1, result.size());
        assertSame(d, result.get(0));
    }

    @Test
    @DisplayName("aggregateByTypeAndLevel - 透传到 mapper")
    void aggregateByTypeAndLevel_passThrough() {
        Map<String, Object> row = Map.of("alertType", "BUDGET", "RED", 3L);
        when(mapper.aggregateByTypeAndLevel(1L)).thenReturn(List.of(row));

        List<Map<String, Object>> result = service.aggregateByTypeAndLevel(1L);

        assertEquals(1, result.size());
        assertEquals("BUDGET", result.get(0).get("alertType"));
    }

    // =========================================================================
    //  工具方法
    // =========================================================================

    /**
     * 构建测试用 AlertDispatchDO
     *
     * @param id     预警 ID
     * @param status 状态
     * @return 预警实体
     */
    private AlertDispatchDO buildDO(Long id, String status) {
        AlertDispatchDO d = new AlertDispatchDO();
        d.setId(id);
        d.setAlertCode("ALERT-" + id);
        d.setAlertType("BUDGET");
        d.setAlertLevel("RED");
        d.setTitle("测试预警 " + id);
        d.setContent("content");
        d.setTargetRole("PMO");
        d.setPushChannels("IN_APP");
        d.setStatus(status);
        d.setRetryCount(0);
        d.setTenantId(1L);
        d.setDispatchedAt(LocalDateTime.now());
        d.setProviderTraceId("");
        return d;
    }
}
