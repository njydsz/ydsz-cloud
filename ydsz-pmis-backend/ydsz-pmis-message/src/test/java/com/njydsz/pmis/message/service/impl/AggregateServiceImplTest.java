package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.entity.MsgAggregateDO;
import com.njydsz.pmis.message.enums.AggregateBatchStatusEnum;
import com.njydsz.pmis.message.mapper.MsgAggregateMapper;
import com.njydsz.pmis.message.service.MessageService;
import com.njydsz.pmis.message.template.TemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AggregateServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AggregateServiceImpl 聚合批次测试")
@ExtendWith(MockitoExtension.class)
class AggregateServiceImplTest {

    @Mock
    private MsgAggregateMapper msgAggregateMapper;
    @Mock
    private MessageService messageService;
    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    @InjectMocks
    private AggregateServiceImpl aggregateService;

    @Test
    @DisplayName("appendOrStart 无 PENDING 批次时新建")
    void appendOrStartShouldCreateWhenAbsent() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(msgAggregateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgAggregateDO result = aggregateService.appendOrStart("RISK", "u1", "EMAIL", "1");

        assertNotNull(result);
        assertEquals(AggregateBatchStatusEnum.PENDING.name(), result.getBatchStatus());
        assertEquals(1, result.getMessageCount());
        verify(msgAggregateMapper).insert(any(MsgAggregateDO.class));
    }

    @Test
    @DisplayName("appendOrStart 存在 PENDING 批次时追加计数")
    void appendOrStartShouldAppendWhenExists() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        MsgAggregateDO existing = new MsgAggregateDO();
        existing.setId("b1");
        existing.setMessageCount(2);
        existing.setBatchStatus(AggregateBatchStatusEnum.PENDING.name());
        when(msgAggregateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        MsgAggregateDO result = aggregateService.appendOrStart("RISK", "u1", "EMAIL", "1");

        assertEquals(3, result.getMessageCount());
        verify(msgAggregateMapper).updateById(existing);
    }

    @Test
    @DisplayName("flushDue 发送到期 READY 批次")
    void flushDueShouldSendDueBatches() {
        MsgAggregateDO batch = new MsgAggregateDO();
        batch.setId("b1");
        batch.setBatchStatus(AggregateBatchStatusEnum.READY.name());
        batch.setAggregateGroup("RISK");
        batch.setReceiver("u1");
        batch.setChannel("EMAIL");
        batch.setMessageCount(3);
        when(msgAggregateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(batch));
        when(templateEngine.render(anyString(), any())).thenReturn("digest");
        MessageResult ok = MessageResult.ok("EMAIL", "trace");
        when(messageService.send(any(MessageRequest.class))).thenReturn(ok);

        int sent = aggregateService.flushDue();

        assertEquals(1, sent);
        assertEquals(AggregateBatchStatusEnum.SENT.name(), batch.getBatchStatus());
    }
}
