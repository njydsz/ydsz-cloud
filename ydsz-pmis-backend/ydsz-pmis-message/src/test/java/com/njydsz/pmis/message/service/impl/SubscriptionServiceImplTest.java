package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.dto.SubscriptionUpsertDTO;
import com.njydsz.pmis.message.entity.MsgSubscriptionDO;
import com.njydsz.pmis.message.enums.SubscriptionStatusEnum;
import com.njydsz.pmis.message.mapper.MsgSubscriptionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SubscriptionServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SubscriptionServiceImpl 订阅服务测试")
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private MsgSubscriptionMapper msgSubscriptionMapper;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @Test
    @DisplayName("upsert 不存在时新建并默认 SUBSCRIBED")
    void upsertShouldInsertSubscribedByDefault() {
        SubscriptionUpsertDTO dto = new SubscriptionUpsertDTO();
        dto.setUserId("u1");
        dto.setTopicCode("RISK");
        dto.setChannel("SMS");
        when(msgSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MsgSubscriptionDO result = subscriptionService.upsert(dto);

        assertTrue(result.getStatus().equals(SubscriptionStatusEnum.SUBSCRIBED.name()));
        verify(msgSubscriptionMapper).insert(any(MsgSubscriptionDO.class));
    }

    @Test
    @DisplayName("isSubscribed 已订阅返回 true")
    void isSubscribedShouldReturnTrueWhenSubscribed() {
        when(msgSubscriptionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertTrue(subscriptionService.isSubscribed("u1", "RISK", "SMS"));
    }

    @Test
    @DisplayName("isSubscribed 未订阅返回 false")
    void isSubscribedShouldReturnFalseWhenUnsubscribed() {
        when(msgSubscriptionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertFalse(subscriptionService.isSubscribed("u1", "RISK", "SMS"));
    }

    @Test
    @DisplayName("unsubscribe 更新状态为 UNSUBSCRIBED")
    void unsubscribeShouldUpdateStatus() {
        MsgSubscriptionDO existing = new MsgSubscriptionDO();
        existing.setStatus(SubscriptionStatusEnum.SUBSCRIBED.name());
        when(msgSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        subscriptionService.unsubscribe("u1", "RISK", "SMS");

        assertTrue(existing.getStatus().equals(SubscriptionStatusEnum.UNSUBSCRIBED.name()));
        verify(msgSubscriptionMapper).updateById(existing);
    }
}
