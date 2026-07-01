package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.AlertDispatchDTO;
import com.njydsz.pmis.execution.entity.AlertDispatchDO;
import com.njydsz.pmis.execution.feign.MessageServiceClient;
import com.njydsz.pmis.execution.mapper.AlertDispatchMapper;
import com.njydsz.pmis.message.channel.MessageRequest;
import com.njydsz.pmis.message.channel.MessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预警分级推送服务测试
 */
@DisplayName("AlertDispatchServiceImpl 预警分级")
class AlertDispatchServiceImplTest {

    private AlertDispatchMapper mapper;
    private MessageServiceClient messageClient;
    private AlertDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AlertDispatchMapper.class);
        messageClient = mock(MessageServiceClient.class);
        service = new AlertDispatchServiceImpl(mapper, messageClient);
    }

    @Test
    @DisplayName("submit 黄色预警 → 目标 PM,PMO")
    void submit_yellow() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(mapper.insert(any(AlertDispatchDO.class))).thenAnswer(inv -> {
            AlertDispatchDO d = inv.getArgument(0);
            d.setId(11L);
            return 1;
        });
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("BUDGET");
        dto.setAlertLevel("YELLOW");
        dto.setTitle("项目1 PURCHASE 累计 90%");
        Long id = service.submit(dto);
        assertThat(id).isEqualTo(11L);
        ArgumentCaptor<AlertDispatchDO> cap = ArgumentCaptor.forClass(AlertDispatchDO.class);
        verify(mapper).insert(cap.capture());
        AlertDispatchDO saved = cap.getValue();
        assertThat(saved.getAlertCode()).startsWith("ALT-YELLOW-BUDGET-");
        assertThat(saved.getTargetRole()).isEqualTo("PM,PMO");
        assertThat(saved.getPushChannels()).isEqualTo("IN_APP");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("submit 红色预警 → 目标 PMO,GM,CFO + 邮件")
    void submit_red() {
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(mapper.insert(any(AlertDispatchDO.class))).thenReturn(1);
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("EVM");
        dto.setAlertLevel("red");
        dto.setTitle("CPI 跌破 0.8");
        service.submit(dto);
        ArgumentCaptor<AlertDispatchDO> cap = ArgumentCaptor.forClass(AlertDispatchDO.class);
        verify(mapper).insert(cap.capture());
        AlertDispatchDO saved = cap.getValue();
        assertThat(saved.getAlertLevel()).isEqualTo("RED");
        assertThat(saved.getTargetRole()).isEqualTo("PMO,GM,CFO");
        assertThat(saved.getPushChannels()).isEqualTo("IN_APP,EMAIL");
    }

    @Test
    @DisplayName("submit 自定义 alertCode 走幂等更新")
    void submit_idempotent() {
        AlertDispatchDO exist = new AlertDispatchDO();
        exist.setId(99L);
        exist.setAlertCode("ALT-YELLOW-RISK-001");
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(exist));
        AlertDispatchDTO dto = new AlertDispatchDTO();
        dto.setAlertType("RISK");
        dto.setAlertLevel("YELLOW");
        dto.setTitle("X");
        dto.setAlertCode("ALT-YELLOW-RISK-001");
        Long id = service.submit(dto);
        assertThat(id).isEqualTo(99L);
        verify(mapper, never()).insert(any(AlertDispatchDO.class));
        verify(mapper, times(1)).updateById(any(AlertDispatchDO.class));
    }

    @Test
    @DisplayName("submit 必填校验")
    void submit_validation() {
        assertThatThrownBy(() -> service.submit(null))
                .isInstanceOf(BizException.class);
        AlertDispatchDTO d1 = new AlertDispatchDTO();
        assertThatThrownBy(() -> service.submit(d1)).isInstanceOf(BizException.class);
        AlertDispatchDTO d2 = new AlertDispatchDTO();
        d2.setAlertType("X");
        assertThatThrownBy(() -> service.submit(d2)).isInstanceOf(BizException.class);
        AlertDispatchDTO d3 = new AlertDispatchDTO();
        d3.setAlertType("X");
        d3.setAlertLevel("PURPLE");
        assertThatThrownBy(() -> service.submit(d3)).isInstanceOf(BizException.class);
        AlertDispatchDTO d4 = new AlertDispatchDTO();
        d4.setAlertType("X");
        d4.setAlertLevel("YELLOW");
        d4.setTitle(" ");
        assertThatThrownBy(() -> service.submit(d4)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("dispatchNow 标记 SENT")
    void dispatchNow_ok() {
        AlertDispatchDO d = new AlertDispatchDO();
        d.setId(1L);
        d.setStatus("PENDING");
        d.setAlertType("BUDGET");
        d.setAlertLevel("YELLOW");
        d.setPushChannels("IN_APP");
        when(mapper.selectById(1L)).thenReturn(d);
        when(messageClient.send(any(MessageRequest.class)))
                .thenReturn(R.ok(MessageResult.ok("IN_APP", "trace-1")));
        when(mapper.markSent(ArgumentMatchers.eq(1L), any())).thenReturn(1);
        boolean ok = service.dispatchNow(1L);
        assertThat(ok).isTrue();
        verify(mapper).markSent(ArgumentMatchers.eq(1L), any());
    }

    @Test
    @DisplayName("dispatchNow 已 SENT 直接返回 true")
    void dispatchNow_alreadySent() {
        AlertDispatchDO d = new AlertDispatchDO();
        d.setStatus("SENT");
        when(mapper.selectById(1L)).thenReturn(d);
        boolean ok = service.dispatchNow(1L);
        assertThat(ok).isTrue();
        verify(mapper, never()).markSent(any(), any());
    }

    @Test
    @DisplayName("dispatchNow Feign 失败时 markFailed")
    void dispatchNow_exception() {
        AlertDispatchDO d = new AlertDispatchDO();
        d.setId(1L);
        d.setStatus("PENDING");
        d.setAlertType("BUDGET");
        d.setAlertLevel("YELLOW");
        d.setPushChannels("IN_APP");
        when(mapper.selectById(1L)).thenReturn(d);
        when(messageClient.send(any(MessageRequest.class)))
                .thenReturn(R.ok(MessageResult.fail("IN_APP", "net err")));
        boolean ok = service.dispatchNow(1L);
        assertThat(ok).isFalse();
        verify(mapper).markFailed(ArgumentMatchers.eq(1L), ArgumentMatchers.contains("net err"));
    }

    @Test
    @DisplayName("dispatchNow 工单不存在")
    void dispatchNow_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.dispatchNow(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("retryFailed 扫描并重发")
    void retryFailed() {
        AlertDispatchDO d1 = new AlertDispatchDO();
        d1.setId(1L);
        d1.setStatus("FAILED");
        d1.setAlertType("BUDGET");
        d1.setAlertLevel("YELLOW");
        d1.setPushChannels("IN_APP");
        AlertDispatchDO d2 = new AlertDispatchDO();
        d2.setId(2L);
        d2.setStatus("FAILED");
        d2.setAlertType("EVM");
        d2.setAlertLevel("RED");
        d2.setPushChannels("IN_APP,EMAIL");
        when(mapper.selectRetryable(any(), ArgumentMatchers.anyInt())).thenReturn(List.of(d1, d2));
        when(mapper.selectById(1L)).thenReturn(d1);
        when(mapper.selectById(2L)).thenReturn(d2);
        when(messageClient.send(any(MessageRequest.class)))
                .thenReturn(R.ok(MessageResult.ok("IN_APP", "trace-x")));
        when(mapper.markSent(any(), any())).thenReturn(1);
        int n = service.retryFailed(3);
        assertThat(n).isEqualTo(2);
        verify(mapper, times(2)).incrementRetry(any());
    }

    @Test
    @DisplayName("resolveTargetRoles 黄/红/NORMAL 映射")
    void resolveTargetRoles() {
        assertThat(service.resolveTargetRoles("RED")).containsExactly("PMO", "GM", "CFO");
        assertThat(service.resolveTargetRoles("yellow")).containsExactly("PM", "PMO");
        assertThat(service.resolveTargetRoles("NORMAL")).containsExactly("PM");
        assertThat(service.resolveTargetRoles("XXX")).isEmpty();
        assertThat(service.resolveTargetRoles(null)).isEmpty();
    }

    @Test
    @DisplayName("cancel 已 SENT 拒绝")
    void cancel_alreadySent() {
        AlertDispatchDO d = new AlertDispatchDO();
        d.setStatus("SENT");
        when(mapper.selectById(1L)).thenReturn(d);
        assertThatThrownBy(() -> service.cancel(1L, "x")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("cancel 正常")
    void cancel_ok() {
        AlertDispatchDO d = new AlertDispatchDO();
        d.setStatus("PENDING");
        when(mapper.selectById(1L)).thenReturn(d);
        service.cancel(1L, "误报");
        ArgumentCaptor<AlertDispatchDO> cap = ArgumentCaptor.forClass(AlertDispatchDO.class);
        verify(mapper).updateById(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("CANCELLED");
        assertThat(cap.getValue().getFailReason()).isEqualTo("误报");
    }

    @Test
    @DisplayName("list / aggregate 委托 mapper")
    void delegations() {
        when(mapper.selectByLevelAndStatus(any(), any())).thenReturn(List.of());
        when(mapper.aggregateByTypeAndLevel(any())).thenReturn(List.of());
        assertThat(service.listByLevelAndStatus("YELLOW", "PENDING")).isEmpty();
        assertThat(service.aggregateByTypeAndLevel(1L)).isEmpty();
    }

    @Test
    @DisplayName("dispatchNow 多通道：IN_APP 失败时整体失败")
    void dispatchNow_multiChannel_partialFail() {
        AlertDispatchDO d = new AlertDispatchDO();
        d.setId(5L);
        d.setStatus("PENDING");
        d.setAlertType("EVM");
        d.setAlertLevel("RED");
        d.setPushChannels("IN_APP,EMAIL");
        when(mapper.selectById(5L)).thenReturn(d);
        // IN_APP 成功, EMAIL 失败
        when(messageClient.send(ArgumentMatchers.argThat(req ->
                req != null && "IN_APP".equals(req.getChannel()))))
                .thenReturn(R.ok(MessageResult.ok("IN_APP", "t-1")));
        when(messageClient.send(ArgumentMatchers.argThat(req ->
                req != null && "EMAIL".equals(req.getChannel()))))
                .thenReturn(R.ok(MessageResult.fail("EMAIL", "smtp 521")));
        boolean ok = service.dispatchNow(5L);
        assertThat(ok).isFalse();
        verify(mapper).markFailed(ArgumentMatchers.eq(5L), ArgumentMatchers.contains("smtp 521"));
        verify(mapper, never()).markSent(any(), any());
    }

    @Test
    @DisplayName("dispatchNow Feign 抛异常时整体失败并降级捕获")
    void dispatchNow_feignException() {
        AlertDispatchDO d = new AlertDispatchDO();
        d.setId(6L);
        d.setStatus("PENDING");
        d.setAlertType("RISK");
        d.setAlertLevel("YELLOW");
        d.setPushChannels("IN_APP");
        when(mapper.selectById(6L)).thenReturn(d);
        when(messageClient.send(any(MessageRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));
        boolean ok = service.dispatchNow(6L);
        assertThat(ok).isFalse();
        verify(mapper).markFailed(ArgumentMatchers.eq(6L), ArgumentMatchers.contains("connection refused"));
    }

    @Test
    @DisplayName("dispatchNow 接收人: targetUserIds 优先于 targetRole")
    void resolveReceiver_priority() {
        // 通过 dispatchNow 内部行为间接校验: targetUserIds 出现在 MessageRequest.receiver
        AlertDispatchDO d = new AlertDispatchDO();
        d.setId(7L);
        d.setStatus("PENDING");
        d.setAlertType("BUDGET");
        d.setAlertLevel("YELLOW");
        d.setPushChannels("IN_APP");
        d.setTargetUserIds("101,102");
        d.setTargetRole("PM,PMO");
        when(mapper.selectById(7L)).thenReturn(d);
        when(messageClient.send(any(MessageRequest.class)))
                .thenReturn(R.ok(MessageResult.ok("IN_APP", "t")));
        when(mapper.markSent(any(), any())).thenReturn(1);
        service.dispatchNow(7L);
        ArgumentCaptor<MessageRequest> cap = ArgumentCaptor.forClass(MessageRequest.class);
        verify(messageClient).send(cap.capture());
        assertThat(cap.getValue().getReceiver()).isEqualTo("101,102");
    }
}
