package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.dto.UnsubscribeQueryDTO;
import com.njydsz.pmis.message.entity.MsgSubscriptionDO;
import com.njydsz.pmis.message.enums.SubscriptionStatusEnum;
import com.njydsz.pmis.message.mapper.MsgSubscriptionMapper;
import com.njydsz.pmis.message.service.SubscriptionService;
import com.njydsz.pmis.message.token.UnsubscribeTokenPayload;
import com.njydsz.pmis.message.token.UnsubscribeTokenUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UnsubscribeServiceImpl} 单元测试（P1-5）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("UnsubscribeServiceImpl 退订中心服务测试")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class UnsubscribeServiceImplTest {

    @Mock
    private UnsubscribeTokenUtil unsubscribeTokenUtil;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private MsgSubscriptionMapper msgSubscriptionMapper;

    @Mock
    private MessageProperties messageProperties;

    @InjectMocks
    private UnsubscribeServiceImpl unsubscribeService;

    private MessageProperties.UnsubscribeConfig stubUnsubscribeConfig(boolean enabled) {
        MessageProperties.UnsubscribeConfig config = new MessageProperties.UnsubscribeConfig();
        config.setEnabled(enabled);
        when(messageProperties.getUnsubscribe()).thenReturn(config);
        return config;
    }

    @Test
    @DisplayName("generateToken 委托 token 工具")
    void generateTokenShouldDelegate() {
        when(unsubscribeTokenUtil.generate("u1", "RISK", "EMAIL")).thenReturn("token-xxx");

        String token = unsubscribeService.generateToken("u1", "RISK", "EMAIL");

        assertEquals("token-xxx", token);
        verify(unsubscribeTokenUtil).generate("u1", "RISK", "EMAIL");
    }

    @Test
    @DisplayName("previewToken 委托 token 工具")
    void previewTokenShouldDelegate() {
        UnsubscribeTokenPayload payload = new UnsubscribeTokenPayload("u1", "RISK", "EMAIL", 999999L);
        when(unsubscribeTokenUtil.parseAndVerify("token-xxx")).thenReturn(payload);

        UnsubscribeTokenPayload result = unsubscribeService.previewToken("token-xxx");

        assertEquals(payload, result);
        verify(unsubscribeTokenUtil).parseAndVerify("token-xxx");
    }

    @Test
    @DisplayName("unsubscribeByToken 退订中心关闭时抛业务异常")
    void unsubscribeByTokenShouldRejectWhenDisabled() {
        stubUnsubscribeConfig(false);

        assertThrows(BizException.class, () -> unsubscribeService.unsubscribeByToken("token-xxx"));
        verify(unsubscribeTokenUtil, never()).parseAndVerify(anyString());
    }

    @Test
    @DisplayName("unsubscribeByToken token 校验通过后委托 subscriptionService.unsubscribe")
    void unsubscribeByTokenShouldDelegateToSubscriptionService() {
        stubUnsubscribeConfig(true);
        UnsubscribeTokenPayload payload = new UnsubscribeTokenPayload("u1", "RISK", "EMAIL", 999999L);
        when(unsubscribeTokenUtil.parseAndVerify("token-xxx")).thenReturn(payload);
        MsgSubscriptionDO unsubscribed = new MsgSubscriptionDO();
        unsubscribed.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
        when(subscriptionService.unsubscribe("u1", "RISK", "EMAIL")).thenReturn(unsubscribed);

        MsgSubscriptionDO result = unsubscribeService.unsubscribeByToken("token-xxx");

        assertNotNull(result);
        assertEquals(SubscriptionStatusEnum.UNSUBSCRIBED.name(), result.getStatus());
        verify(subscriptionService).unsubscribe("u1", "RISK", "EMAIL");
    }

    @Test
    @DisplayName("pageUnsubscribed 强制 status=UNSUBSCRIBED 并按 unsubscribedAt 倒序")
    void pageUnsubscribedShouldFilterByUnsubscribedStatus() {
        UnsubscribeQueryDTO query = new UnsubscribeQueryDTO();
        query.setUserId("u1");
        Page<MsgSubscriptionDO> mockPage = new Page<>();
        mockPage.setTotal(5);
        when(msgSubscriptionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        PageResult<MsgSubscriptionDO> result = unsubscribeService.pageUnsubscribed(query);

        assertNotNull(result);
        assertEquals(5L, result.getTotal());
        verify(msgSubscriptionMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("pageUnsubscribed 入参为 null 时使用默认分页")
    void pageUnsubscribedShouldHandleNullQuery() {
        Page<MsgSubscriptionDO> mockPage = new Page<>();
        when(msgSubscriptionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        PageResult<MsgSubscriptionDO> result = unsubscribeService.pageUnsubscribed(null);

        assertNotNull(result);
        verify(msgSubscriptionMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("resubscribe 已存在 UNSUBSCRIBED 记录时恢复为 SUBSCRIBED 并清空退订时间")
    void resubscribeShouldRestoreExistingRecord() {
        MsgSubscriptionDO existing = new MsgSubscriptionDO();
        existing.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
        existing.setUnsubscribedAt(java.time.LocalDateTime.now());
        when(msgSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        unsubscribeService.resubscribe("u1", "RISK", "EMAIL");

        assertEquals(SubscriptionStatusEnum.SUBSCRIBED.name(), existing.getStatus());
        assertEquals(null, existing.getUnsubscribedAt());
        verify(msgSubscriptionMapper).updateById(existing);
    }

    @Test
    @DisplayName("resubscribe 已是 SUBSCRIBED 时幂等不更新")
    void resubscribeShouldBeIdempotentWhenAlreadySubscribed() {
        MsgSubscriptionDO existing = new MsgSubscriptionDO();
        existing.setStatus(SubscriptionStatusEnum.SUBSCRIBED.name());
        when(msgSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        unsubscribeService.resubscribe("u1", "RISK", "EMAIL");

        verify(msgSubscriptionMapper, never()).updateById(any(MsgSubscriptionDO.class));
        verify(msgSubscriptionMapper, never()).insert(any(MsgSubscriptionDO.class));
    }

    @Test
    @DisplayName("resubscribe 无记录时新建 SUBSCRIBED 记录")
    void resubscribeShouldInsertWhenNotExists() {
        when(msgSubscriptionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        unsubscribeService.resubscribe("u1", "RISK", "EMAIL");

        verify(msgSubscriptionMapper).insert(any(MsgSubscriptionDO.class));
        verify(msgSubscriptionMapper, never()).updateById(any(MsgSubscriptionDO.class));
    }

    @Test
    @DisplayName("resubscribe 参数缺失抛参数错误")
    void resubscribeShouldRejectBlankArgs() {
        assertThrows(BizException.class, () -> unsubscribeService.resubscribe("", "RISK", "EMAIL"));
        assertThrows(BizException.class, () -> unsubscribeService.resubscribe("u1", "", "EMAIL"));
        assertThrows(BizException.class, () -> unsubscribeService.resubscribe("u1", "RISK", ""));
    }
}
