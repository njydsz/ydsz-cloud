package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.NotificationClient;
import com.njydsz.pmis.common.feign.dto.NotificationFeignDTO;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.entity.FlowNotifyOutboxDO;
import com.njydsz.pmis.workflow.mapper.FlowNotifyOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowNotifyOutboxServiceImpl 单元测试
 *
 * <p>P2-1：覆盖工作流通知外发箱服务的核心场景，验证入箱默认值、扫描投递、失败重试退避、
 * 死信转换、死信查询与人工重投、通知 DTO 构造等行为。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>saveOutbox：写入时填充默认值（status=PENDING、retryCount=0、maxRetries=5、nextRetryAt=now）</li>
 *   <li>scanAndDeliver：投递成功调用 markSent</li>
 *   <li>scanAndDeliver：投递返回失败码时按指数退避重试（markRetry）</li>
 *   <li>scanAndDeliver：重试次数达上限转为死信（markDead）</li>
 *   <li>scanAndDeliver：NotificationClient 抛异常时走 handleFailure</li>
 *   <li>scanAndDeliver：空列表不调用 NotificationClient</li>
 *   <li>listDeadEvents：查询 status=DEAD 的事件</li>
 *   <li>retryDeadEvent：死信重置为 PENDING、retryCount=0、nextRetryAt=now</li>
 *   <li>buildNotificationDTO：从 payload JSON 提取字段并解析 targetUserIds</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class FlowNotifyOutboxServiceImplTest {

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private FlowNotifyOutboxMapper flowNotifyOutboxMapper;

    private FlowNotifyOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        // 构造器方式注入被测对象（顺序：NotificationClient, FlowNotifyOutboxMapper）
        service = new FlowNotifyOutboxServiceImpl(notificationClient, flowNotifyOutboxMapper);
    }

    // ============ saveOutbox 场景 ============

    @Test
    @DisplayName("saveOutbox：未设置默认字段时填充 PENDING/0/5/now 并写入 mapper")
    void saveOutboxShouldFillDefaultsAndInsert() {
        FlowNotifyOutboxDO event = new FlowNotifyOutboxDO();
        event.setId(1L);
        event.setEventType("TASK_CREATED");
        event.setBizType("WORKFLOW_TASK");
        event.setBizId(100L);
        // 故意不设置 status / retryCount / maxRetries / nextRetryAt / tenantId

        Long id = service.saveOutbox(event);

        assertThat(id).isEqualTo(1L);

        ArgumentCaptor<FlowNotifyOutboxDO> captor = ArgumentCaptor.forClass(FlowNotifyOutboxDO.class);
        verify(flowNotifyOutboxMapper, times(1)).insert(captor.capture());
        FlowNotifyOutboxDO saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getMaxRetries()).isEqualTo(5);
        assertThat(saved.getNextRetryAt()).isNotNull();
        // TenantContext 未设置时默认返回 1L
        assertThat(saved.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("saveOutbox：传入 null 时返回 null 且不调用 mapper")
    void saveOutboxShouldReturnNullWhenEventIsNull() {
        Long id = service.saveOutbox(null);

        assertThat(id).isNull();
        verify(flowNotifyOutboxMapper, never()).insert(any(FlowNotifyOutboxDO.class));
    }

    // ============ scanAndDeliver 场景 ============

    @Test
    @DisplayName("scanAndDeliver：投递成功时调用 markSent 并返回成功条数")
    void scanAndDeliverShouldMarkSentWhenDeliverSuccess() {
        FlowNotifyOutboxDO event = buildPendingEvent(1L, 0);
        when(flowNotifyOutboxMapper.selectPendingForSend(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(event));
        when(notificationClient.send(any(NotificationFeignDTO.class)))
                .thenReturn(Result.ok(1));

        int success = service.scanAndDeliver(50);

        assertThat(success).isEqualTo(1);
        verify(flowNotifyOutboxMapper, times(1)).markSent(eq(1L), any(LocalDateTime.class));
        verify(flowNotifyOutboxMapper, never()).markRetry(any(), anyString(), any());
        verify(flowNotifyOutboxMapper, never()).markDead(any(), anyString());
    }

    @Test
    @DisplayName("scanAndDeliver：投递返回失败码时调用 markRetry，nextRetryAt 按指数退避（首次 30s）")
    void scanAndDeliverShouldMarkRetryWithExponentialBackoffWhenFailedCode() {
        // retryCount=0 → newRetryCount=1 < maxRetries(5) → markRetry，退避 BACKOFF_SECONDS[0]=30s
        FlowNotifyOutboxDO event = buildPendingEvent(2L, 0);
        when(flowNotifyOutboxMapper.selectPendingForSend(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(event));
        when(notificationClient.send(any(NotificationFeignDTO.class)))
                .thenReturn(Result.failed(-1, "通知中心内部错误"));

        LocalDateTime before = LocalDateTime.now();
        int success = service.scanAndDeliver(50);
        LocalDateTime after = LocalDateTime.now();

        assertThat(success).isEqualTo(0);

        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(flowNotifyOutboxMapper, times(1)).markRetry(eq(2L), eq("投递返回失败码"), nextRetryCaptor.capture());
        // 退避 30s：nextRetryAt 落在 [before+28s, after+32s] 区间内
        assertThat(nextRetryCaptor.getValue())
                .isBetween(before.plusSeconds(28), after.plusSeconds(32));
        verify(flowNotifyOutboxMapper, never()).markSent(any(), any());
        verify(flowNotifyOutboxMapper, never()).markDead(any(), anyString());
    }

    @Test
    @DisplayName("scanAndDeliver：retryCount 达到 maxRetries 时转为死信 markDead")
    void scanAndDeliverShouldMarkDeadWhenRetryCountReachesMax() {
        // retryCount=4 → newRetryCount=5 >= maxRetries(5) → markDead
        FlowNotifyOutboxDO event = buildPendingEvent(3L, 4);
        when(flowNotifyOutboxMapper.selectPendingForSend(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(event));
        when(notificationClient.send(any(NotificationFeignDTO.class)))
                .thenReturn(Result.failed(-1, "通知中心内部错误"));

        int success = service.scanAndDeliver(50);

        assertThat(success).isEqualTo(0);
        verify(flowNotifyOutboxMapper, times(1)).markDead(eq(3L), eq("投递返回失败码"));
        verify(flowNotifyOutboxMapper, never()).markRetry(any(), anyString(), any());
        verify(flowNotifyOutboxMapper, never()).markSent(any(), any());
    }

    @Test
    @DisplayName("scanAndDeliver：NotificationClient 抛异常时走 handleFailure 并 markRetry")
    void scanAndDeliverShouldHandleFailureWhenClientThrowsException() {
        FlowNotifyOutboxDO event = buildPendingEvent(4L, 0);
        when(flowNotifyOutboxMapper.selectPendingForSend(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(event));
        when(notificationClient.send(any(NotificationFeignDTO.class)))
                .thenThrow(new RuntimeException("连接超时"));

        int success = service.scanAndDeliver(50);

        assertThat(success).isEqualTo(0);
        // 异常被 scanAndDeliver 捕获后转 handleFailure：retryCount 0→1，调用 markRetry
        verify(flowNotifyOutboxMapper, times(1))
                .markRetry(eq(4L), eq("投递异常: 连接超时"), any(LocalDateTime.class));
        verify(flowNotifyOutboxMapper, never()).markSent(any(), any());
        verify(flowNotifyOutboxMapper, never()).markDead(any(), anyString());
    }

    @Test
    @DisplayName("scanAndDeliver：扫描结果为空时不调用 NotificationClient 并返回 0")
    void scanAndDeliverShouldSkipClientWhenPendingEmpty() {
        when(flowNotifyOutboxMapper.selectPendingForSend(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.emptyList());

        int success = service.scanAndDeliver(50);

        assertThat(success).isEqualTo(0);
        verify(notificationClient, never()).send(any(NotificationFeignDTO.class));
        verify(flowNotifyOutboxMapper, never()).markSent(any(), any());
        verify(flowNotifyOutboxMapper, never()).markRetry(any(), anyString(), any());
        verify(flowNotifyOutboxMapper, never()).markDead(any(), anyString());
    }

    // ============ listDeadEvents 场景 ============

    @Test
    @DisplayName("listDeadEvents：调用 mapper 查询死信事件并返回列表")
    void listDeadEventsShouldReturnDeadEventsFromMapper() {
        FlowNotifyOutboxDO dead1 = buildDeadEvent(10L);
        FlowNotifyOutboxDO dead2 = buildDeadEvent(11L);
        when(flowNotifyOutboxMapper.selectList(any())).thenReturn(List.of(dead1, dead2));

        List<FlowNotifyOutboxDO> deadEvents = service.listDeadEvents(20);

        assertThat(deadEvents).hasSize(2);
        assertThat(deadEvents).extracting(FlowNotifyOutboxDO::getId).containsExactly(10L, 11L);
        assertThat(deadEvents).allMatch(e -> "DEAD".equals(e.getStatus()));
        verify(flowNotifyOutboxMapper, times(1)).selectList(any());
    }

    // ============ retryDeadEvent 场景 ============

    @Test
    @DisplayName("retryDeadEvent：将 DEAD 事件重置为 PENDING、retryCount=0、nextRetryAt=now")
    void retryDeadEventShouldResetToPending() {
        FlowNotifyOutboxDO dead = buildDeadEvent(20L);
        dead.setRetryCount(5);
        dead.setErrorMsg("累计失败");
        when(flowNotifyOutboxMapper.selectById(20L)).thenReturn(dead);

        LocalDateTime before = LocalDateTime.now();
        boolean ok = service.retryDeadEvent(20L);
        LocalDateTime after = LocalDateTime.now();

        assertThat(ok).isTrue();

        ArgumentCaptor<FlowNotifyOutboxDO> captor = ArgumentCaptor.forClass(FlowNotifyOutboxDO.class);
        verify(flowNotifyOutboxMapper, times(1)).updateById(captor.capture());
        FlowNotifyOutboxDO updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("PENDING");
        assertThat(updated.getRetryCount()).isEqualTo(0);
        assertThat(updated.getErrorMsg()).isNull();
        assertThat(updated.getNextRetryAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("retryDeadEvent：非 DEAD 状态事件返回 false 且不更新")
    void retryDeadEventShouldReturnFalseWhenNotDead() {
        FlowNotifyOutboxDO pending = buildPendingEvent(21L, 1);
        when(flowNotifyOutboxMapper.selectById(21L)).thenReturn(pending);

        boolean ok = service.retryDeadEvent(21L);

        assertThat(ok).isFalse();
        verify(flowNotifyOutboxMapper, never()).updateById(any(FlowNotifyOutboxDO.class));
    }

    // ============ buildNotificationDTO 场景（通过 scanAndDeliver 成功路径捕获 DTO） ============

    @Test
    @DisplayName("buildNotificationDTO：从 payload JSON 提取 title/content/level/receiverId，targetUserIds 逗号分隔解析")
    void buildNotificationDTOShouldParsePayloadAndTargetUserIds() {
        FlowNotifyOutboxDO event = buildPendingEvent(30L, 0);
        event.setEventType("TASK_CREATED");
        event.setBizType("WORKFLOW_TASK");
        event.setBizId(500L);
        event.setPayload("{\"title\":\"任务待办\",\"content\":\"您有新任务待处理\",\"level\":\"URGENT\",\"receiverId\":1001}");
        event.setTargetUserIds("1, 2, 3");
        when(flowNotifyOutboxMapper.selectPendingForSend(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(event));
        when(notificationClient.send(any(NotificationFeignDTO.class)))
                .thenReturn(Result.ok(1));

        service.scanAndDeliver(50);

        ArgumentCaptor<NotificationFeignDTO> dtoCaptor = ArgumentCaptor.forClass(NotificationFeignDTO.class);
        verify(notificationClient, times(1)).send(dtoCaptor.capture());
        NotificationFeignDTO dto = dtoCaptor.getValue();
        // payload JSON 覆盖默认值（默认 title=eventType、content=payload、level=INFO）
        assertThat(dto.getTitle()).isEqualTo("任务待办");
        assertThat(dto.getContent()).isEqualTo("您有新任务待处理");
        assertThat(dto.getLevel()).isEqualTo("URGENT");
        assertThat(dto.getReceiverId()).isEqualTo(1001L);
        // targetUserIds 逗号分隔解析为批量接收人
        assertThat(dto.getReceiverIds()).containsExactly(1L, 2L, 3L);
        // 固定默认值
        assertThat(dto.getCategory()).isEqualTo("WORKFLOW");
        assertThat(dto.getBizType()).isEqualTo("WORKFLOW_TASK");
        assertThat(dto.getBizId()).isEqualTo("500");
    }

    // ============ 辅助方法 ============

    /**
     * 构造 PENDING 事件
     *
     * @param id         事件 ID
     * @param retryCount 已重试次数
     */
    private FlowNotifyOutboxDO buildPendingEvent(Long id, int retryCount) {
        FlowNotifyOutboxDO event = new FlowNotifyOutboxDO();
        event.setId(id);
        event.setTenantId(1L);
        event.setEventType("TASK_CREATED");
        event.setBizType("WORKFLOW_TASK");
        event.setBizId(100L);
        event.setStatus("PENDING");
        event.setRetryCount(retryCount);
        event.setMaxRetries(5);
        event.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        return event;
    }

    /**
     * 构造死信事件
     */
    private FlowNotifyOutboxDO buildDeadEvent(Long id) {
        FlowNotifyOutboxDO event = new FlowNotifyOutboxDO();
        event.setId(id);
        event.setTenantId(1L);
        event.setEventType("TASK_CREATED");
        event.setBizType("WORKFLOW_TASK");
        event.setStatus("DEAD");
        event.setRetryCount(5);
        event.setMaxRetries(5);
        event.setErrorMsg("投递失败");
        return event;
    }
}
