package com.njydsz.message.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.dto.MessageItemRequestDTO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MessageSendResultVO;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.event.OutboxDomainEventPublisher;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.producer.MessageQueueOperations;
import com.njydsz.message.server.service.batch.BatchService;
import com.njydsz.message.server.service.chain.SendPipelineFacade;
import com.njydsz.message.server.service.core.DeliveryTimeOptimizer;
import com.njydsz.message.server.service.core.MessageQueryService;
import com.njydsz.message.server.service.core.MessageRenderService;
import com.njydsz.message.server.service.core.MessageSendService;
import com.njydsz.message.server.service.core.MessageSendTxService;
import com.njydsz.message.server.service.core.MessageTraceService;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

  @InjectMocks
  private MessageServiceImpl messageService;

  @Mock
  private SnowflakeIdGenerator snowflakeIdGenerator;

  @Mock
  private ChannelRouter channelRouter;

  @Mock
  private MsgLogRepository msgLogRepository;

  @Mock
  private MessageProperties messageProperties;

  @Mock
  private MessageMetrics messageMetrics;

  @Mock
  private MessageTraceService messageTraceService;

  @Mock
  private DeliveryTimeOptimizer deliveryTimeOptimizer;

  @Mock
  private ObjectProvider<MessageQueueOperations> mqProducerProvider;

  @Mock
  private BatchService batchService;

  @Mock
  private MessageSendService messageSendService;

  @Mock
  private MessageQueryService messageQueryService;

  @Mock
  private AggregatePersistenceService aggregatePersistenceService;

  @Mock
  private ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  @Mock
  private OutboxDomainEventPublisher outboxPublisher;

  @Mock
  private MessageRenderService messageRenderService;

  @Mock
  private SendPipelineFacade sendPipelineFacade;

  @Mock
  private MessageSendTxService messageSendTxService;

  @Test
  @DisplayName("取消定时消息 - 正常取消成功")
  void cancelScheduledMessage_normalCancel_success() {
    MsgLogVO logVO = new MsgLogVO();
    logVO.setMsgId("msg-001");
    logVO.setStatus(MessageStatusEnum.SCHEDULED.name());
    logVO.setChannel("INAPP");
    when(msgLogRepository.findOne(any())).thenReturn(Optional.of(logVO));
    when(msgLogRepository.update(any(MsgLogVO.class))).thenReturn(true);

    MessageSendResultVO result = messageService.cancelScheduledMessage("msg-001");

    assertThat(result.isSuccess()).isTrue();
    verify(msgLogRepository).update(any(MsgLogVO.class));
  }

  @Test
  @DisplayName("取消定时消息 - 消息不存在时返回失败")
  void cancelScheduledMessage_notFound_returnsFail() {
    when(msgLogRepository.findOne(any())).thenReturn(Optional.empty());

    MessageSendResultVO result = messageService.cancelScheduledMessage("msg-not-exist");

    assertThat(result.isSuccess()).isFalse();
    verify(msgLogRepository, never()).update(any(MsgLogVO.class));
  }

  @Test
  @DisplayName("取消定时消息 - 非SCHEDULED状态拒绝取消")
  void cancelScheduledMessage_wrongStatus_returnsFail() {
    MsgLogVO logVO = new MsgLogVO();
    logVO.setMsgId("msg-002");
    logVO.setStatus(MessageStatusEnum.SUCCESS.name());
    logVO.setChannel("SMS");
    when(msgLogRepository.findOne(any())).thenReturn(Optional.of(logVO));

    MessageSendResultVO result = messageService.cancelScheduledMessage("msg-002");

    assertThat(result.isSuccess()).isFalse();
    verify(msgLogRepository, never()).update(any(MsgLogVO.class));
  }
}
