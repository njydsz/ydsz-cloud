package com.njydsz.pmis.message.server.service.impl.batch;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.domain.entity.batch.MsgAggregateDO;
import com.njydsz.pmis.message.domain.enums.batch.AggregateBatchStatusEnum;
import com.njydsz.pmis.message.infra.mapper.batch.MsgAggregateMapper;
import com.njydsz.pmis.message.server.service.core.MessageService;
import com.njydsz.pmis.message.server.service.template.TemplateService;
import com.njydsz.pmis.message.server.template.TemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AggregateServiceImpl 聚合批次服务单元测试。
 *
 * <p>P1-2: 验证 sendBatch CAS 占有(READY→SENDING)逻辑。
 */
@DisplayName("AggregateServiceImpl 聚合批次服务测试")
@ExtendWith(MockitoExtension.class)
class AggregateServiceImplTest {

    @Mock
    private MsgAggregateMapper msgAggregateMapper;

    @Mock
    private MessageService messageService;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private TemplateService templateService;

    @Mock
    private org.redisson.api.RedissonClient redissonClient;

    @InjectMocks
    private AggregateServiceImpl service;

    private MsgAggregateDO buildReadyBatch() {
        MsgAggregateDO batch = new MsgAggregateDO();
        batch.setId("batch-001");
        batch.setAggregateGroup("ORDER");
        batch.setReceiver("user-001");
        batch.setChannel("INAPP");
        batch.setBatchStatus(AggregateBatchStatusEnum.READY.name());
        batch.setMessageCount(3);
        batch.setTenantId("1");
        return batch;
    }

    @Nested
    @DisplayName("flushDue() CAS 占有逻辑")
    class FlushDueCasTest {

        @Test
        @DisplayName("CAS 占有成功(update=1) + 发送成功 → SENT")
        void casSuccessAndSendSuccess() {
            MsgAggregateDO batch = buildReadyBatch();
            when(msgAggregateMapper.selectList(any())).thenReturn(List.of(batch));
            // CAS 占有成功
            when(msgAggregateMapper.update(eq(null), any(LambdaUpdateWrapper.class)))
                    .thenReturn(1);
            // 发送成功
            MessageResult okResult = MessageResult.ok("INAPP", "trace-001");
            when(messageService.send(any(MessageRequest.class))).thenReturn(okResult);
            when(templateEngine.render(any(), any())).thenReturn("摘要内容");
            when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(null);

            int sent = service.flushDue();

            assertThat(sent).isEqualTo(1);
            // CAS: READY→SENDING
            verify(msgAggregateMapper, times(1)).update(eq(null), any(LambdaUpdateWrapper.class));
            // 发送成功后 updateById(SENT)
            verify(msgAggregateMapper, times(1)).updateById(any());
        }

        @Test
        @DisplayName("CAS 占有失败(update=0) → 跳过发送,不调 messageService")
        void casFailureShouldSkipSend() {
            MsgAggregateDO batch = buildReadyBatch();
            when(msgAggregateMapper.selectList(any())).thenReturn(List.of(batch));
            // CAS 占有失败(已被其他实例占有)
            when(msgAggregateMapper.update(eq(null), any(LambdaUpdateWrapper.class)))
                    .thenReturn(0);

            int sent = service.flushDue();

            assertThat(sent).isEqualTo(0);
            verify(messageService, never()).send(any());
        }

        @Test
        @DisplayName("CAS 占有成功但发送失败 → 回退 READY")
        void sendFailureShouldRevertToReady() {
            MsgAggregateDO batch = buildReadyBatch();
            when(msgAggregateMapper.selectList(any())).thenReturn(List.of(batch));
            // CAS 占有成功
            when(msgAggregateMapper.update(eq(null), any(LambdaUpdateWrapper.class)))
                    .thenReturn(1)  // 第一次: CAS 占有
                    .thenReturn(1); // 第二次: 回退 READY
            // 发送失败
            MessageResult failResult = MessageResult.fail("INAPP", "发送失败");
            when(messageService.send(any(MessageRequest.class))).thenReturn(failResult);
            when(templateEngine.render(any(), any())).thenReturn("摘要");
            when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(null);

            int sent = service.flushDue();

            assertThat(sent).isEqualTo(0);
            // 两次 update: CAS 占有 + 回退 READY
            verify(msgAggregateMapper, times(2)).update(eq(null), any(LambdaUpdateWrapper.class));
            verify(msgAggregateMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("无到期批次 → 返回 0")
        void noDueBatch() {
            when(msgAggregateMapper.selectList(any())).thenReturn(List.of());

            int sent = service.flushDue();

            assertThat(sent).isEqualTo(0);
            verify(msgAggregateMapper, never()).update(any(), any());
            verify(messageService, never()).send(any());
        }
    }
}
