package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.dto.UnsubscribeQueryDTO;
import com.njydsz.pmis.message.entity.MsgSubscriptionDO;
import com.njydsz.pmis.message.enums.SubscriptionStatusEnum;
import com.njydsz.pmis.message.service.UnsubscribeService;
import com.njydsz.pmis.message.token.UnsubscribeTokenPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UnsubscribeController} 单元测试（P1-5）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("UnsubscribeController 退订中心测试")
@ExtendWith(MockitoExtension.class)
class UnsubscribeControllerTest {

    @Mock
    private UnsubscribeService unsubscribeService;

    @InjectMocks
    private UnsubscribeController unsubscribeController;

    @Test
    @DisplayName("oneClick 合法 token 委托 service 并返回退订记录")
    void oneClickShouldDelegateToService() {
        MsgSubscriptionDO unsubscribed = new MsgSubscriptionDO();
        unsubscribed.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
        when(unsubscribeService.unsubscribeByToken("token-xxx")).thenReturn(unsubscribed);

        Result<MsgSubscriptionDO> result = unsubscribeController.oneClick("token-xxx");

        assertTrue(result.isSuccess());
        assertEquals(SubscriptionStatusEnum.UNSUBSCRIBED.name(), result.getData().getStatus());
        verify(unsubscribeService).unsubscribeByToken("token-xxx");
    }

    @Test
    @DisplayName("oneClick 空 token 返回参数错误")
    void oneClickShouldReturnBadRequestWhenBlank() {
        Result<MsgSubscriptionDO> result = unsubscribeController.oneClick("");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        verify(unsubscribeService, never()).unsubscribeByToken(any());
    }

    @Test
    @DisplayName("oneClick null token 返回参数错误")
    void oneClickShouldReturnBadRequestWhenNull() {
        Result<MsgSubscriptionDO> result = unsubscribeController.oneClick(null);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        verify(unsubscribeService, never()).unsubscribeByToken(any());
    }

    @Test
    @DisplayName("preview 合法 token 返回载荷")
    void previewShouldReturnPayload() {
        UnsubscribeTokenPayload payload = new UnsubscribeTokenPayload("u1", "RISK", "EMAIL", 999999L);
        when(unsubscribeService.previewToken("token-xxx")).thenReturn(payload);

        Result<UnsubscribeTokenPayload> result = unsubscribeController.preview("token-xxx");

        assertTrue(result.isSuccess());
        assertEquals("u1", result.getData().getUserId());
        assertEquals("RISK", result.getData().getTopicCode());
        assertEquals("EMAIL", result.getData().getChannel());
    }

    @Test
    @DisplayName("preview 空 token 返回参数错误")
    void previewShouldReturnBadRequestWhenBlank() {
        Result<UnsubscribeTokenPayload> result = unsubscribeController.preview("");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        verify(unsubscribeService, never()).previewToken(any());
    }

    @Test
    @DisplayName("page 委托 service 并返回分页结果")
    void pageShouldDelegateToService() {
        UnsubscribeQueryDTO query = new UnsubscribeQueryDTO();
        query.setUserId("u1");
        PageResult<MsgSubscriptionDO> mockResult = PageResult.of(
                List.of(new MsgSubscriptionDO()), 1L, 1L, 10L);
        when(unsubscribeService.pageUnsubscribed(query)).thenReturn(mockResult);

        Result<PageResult<MsgSubscriptionDO>> result = unsubscribeController.page(query);

        assertTrue(result.isSuccess());
        assertEquals(1L, result.getData().getTotal());
        verify(unsubscribeService).pageUnsubscribed(query);
    }

    @Test
    @DisplayName("page 入参为 null 时仍正常委托")
    void pageShouldHandleNullQuery() {
        PageResult<MsgSubscriptionDO> mockResult = PageResult.empty();
        when(unsubscribeService.pageUnsubscribed(any())).thenReturn(mockResult);

        Result<PageResult<MsgSubscriptionDO>> result = unsubscribeController.page(null);

        assertTrue(result.isSuccess());
        verify(unsubscribeService).pageUnsubscribed(any());
    }

    @Test
    @DisplayName("resubscribe 合法参数委托 service 并返回成功")
    void resubscribeShouldDelegateToService() {
        Result<Void> result = unsubscribeController.resubscribe("u1", "RISK", "EMAIL");

        assertTrue(result.isSuccess());
        verify(unsubscribeService).resubscribe("u1", "RISK", "EMAIL");
    }

    @Test
    @DisplayName("resubscribe 任一参数为空返回参数错误")
    void resubscribeShouldReturnBadRequestWhenBlank() {
        assertFalse(unsubscribeController.resubscribe("", "RISK", "EMAIL").isSuccess());
        assertFalse(unsubscribeController.resubscribe("u1", "", "EMAIL").isSuccess());
        assertFalse(unsubscribeController.resubscribe("u1", "RISK", "").isSuccess());
        assertFalse(unsubscribeController.resubscribe(null, "RISK", "EMAIL").isSuccess());
        verify(unsubscribeService, never()).resubscribe(any(), any(), any());
    }
}
