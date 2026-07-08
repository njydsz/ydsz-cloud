package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.dto.ChannelStatsVO;
import com.njydsz.pmis.message.dto.CostStatsVO;
import com.njydsz.pmis.message.dto.FunnelStatsVO;
import com.njydsz.pmis.message.dto.MessageStatsVO;
import com.njydsz.pmis.message.dto.ReceiptStatsVO;
import com.njydsz.pmis.message.service.MessageStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageStatsController} 单元测试（P1-2 可观测看板）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageStatsController 统计看板测试")
@ExtendWith(MockitoExtension.class)
class MessageStatsControllerTest {

    @Mock
    private MessageStatsService messageStatsService;

    @InjectMocks
    private MessageStatsController messageStatsController;

    @Test
    @DisplayName("overview 委托 service 并返回成功")
    void overviewShouldDelegateToService() {
        MessageStatsVO vo = new MessageStatsVO();
        vo.setTotal(100);
        when(messageStatsService.getOverview(null, null)).thenReturn(vo);

        Result<MessageStatsVO> result = messageStatsController.overview(null, null);

        assertTrue(result.isSuccess());
        assertEquals(100, result.getData().getTotal());
        verify(messageStatsService).getOverview(null, null);
    }

    @Test
    @DisplayName("overview 带时间参数正确传递")
    void overviewShouldPassTimeParams() {
        LocalDateTime start = LocalDateTime.now().minusHours(12);
        LocalDateTime end = LocalDateTime.now();
        MessageStatsVO vo = new MessageStatsVO();
        when(messageStatsService.getOverview(start, end)).thenReturn(vo);

        Result<MessageStatsVO> result = messageStatsController.overview(start, end);

        assertTrue(result.isSuccess());
        verify(messageStatsService).getOverview(start, end);
    }

    @Test
    @DisplayName("channelStats 委托 service 并返回列表")
    void channelStatsShouldDelegateToService() {
        ChannelStatsVO chVo = new ChannelStatsVO();
        chVo.setChannel("SMS");
        chVo.setTotal(50);
        when(messageStatsService.getChannelStats(null, null))
                .thenReturn(List.of(chVo));

        Result<List<ChannelStatsVO>> result = messageStatsController.channelStats(null, null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().size());
        assertEquals("SMS", result.getData().get(0).getChannel());
        verify(messageStatsService).getChannelStats(null, null);
    }

    @Test
    @DisplayName("channelStats 无数据时返回空列表")
    void channelStatsShouldReturnEmptyListWhenNoData() {
        when(messageStatsService.getChannelStats(null, null))
                .thenReturn(Collections.emptyList());

        Result<List<ChannelStatsVO>> result = messageStatsController.channelStats(null, null);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    @DisplayName("receiptStats 委托 service 并返回成功")
    void receiptStatsShouldDelegateToService() {
        ReceiptStatsVO vo = new ReceiptStatsVO();
        vo.setTotal(100);
        vo.setDeliveryRate(90.0);
        when(messageStatsService.getReceiptStats(null, null)).thenReturn(vo);

        Result<ReceiptStatsVO> result = messageStatsController.receiptStats(null, null);

        assertTrue(result.isSuccess());
        assertEquals(100, result.getData().getTotal());
        assertEquals(90.0, result.getData().getDeliveryRate(), 0.01);
        verify(messageStatsService).getReceiptStats(null, null);
    }

    @Test
    @DisplayName("P2-2: funnel 委托 service 并返回漏斗数据")
    void funnelShouldDelegateToService() {
        FunnelStatsVO vo = new FunnelStatsVO();
        vo.setSent(100);
        vo.setDelivered(80);
        vo.setRead(50);
        vo.setClicked(20);
        vo.setOverallConversionRate(20.0);
        when(messageStatsService.getFunnel(null, null, null, null)).thenReturn(vo);

        Result<FunnelStatsVO> result = messageStatsController.funnel(null, null, null, null);

        assertTrue(result.isSuccess());
        assertEquals(100, result.getData().getSent());
        assertEquals(20.0, result.getData().getOverallConversionRate(), 0.01);
        verify(messageStatsService).getFunnel(null, null, null, null);
    }

    @Test
    @DisplayName("P2-2: funnel 带通道和模板参数正确传递")
    void funnelShouldPassChannelAndTemplateParams() {
        FunnelStatsVO vo = new FunnelStatsVO();
        vo.setSent(50);
        vo.setChannel("SMS");
        when(messageStatsService.getFunnel(null, null, "SMS", "TPL_ALERT")).thenReturn(vo);

        Result<FunnelStatsVO> result = messageStatsController.funnel(null, null, "SMS", "TPL_ALERT");

        assertTrue(result.isSuccess());
        assertEquals("SMS", result.getData().getChannel());
        verify(messageStatsService).getFunnel(null, null, "SMS", "TPL_ALERT");
    }

    @Test
    @DisplayName("P2-4: cost 委托 service 并返回成本看板数据")
    void costShouldDelegateToService() {
        CostStatsVO vo = new CostStatsVO();
        vo.setTotalCost(new BigDecimal("4.80"));
        CostStatsVO.ChannelCost cc = new CostStatsVO.ChannelCost();
        cc.setChannel("SMS");
        cc.setMessageCount(100L);
        cc.setUnitPrice(new BigDecimal("0.0450"));
        cc.setTotalCost(new BigDecimal("4.500"));
        vo.setChannels(List.of(cc));
        when(messageStatsService.getCostStats(null, null)).thenReturn(vo);

        Result<CostStatsVO> result = messageStatsController.cost(null, null);

        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("4.80").compareTo(result.getData().getTotalCost()));
        assertEquals(1, result.getData().getChannels().size());
        assertEquals("SMS", result.getData().getChannels().get(0).getChannel());
        verify(messageStatsService).getCostStats(null, null);
    }

    @Test
    @DisplayName("P2-4: cost 带时间参数正确传递")
    void costShouldPassTimeParams() {
        LocalDateTime start = LocalDateTime.now().minusHours(12);
        LocalDateTime end = LocalDateTime.now();
        CostStatsVO vo = new CostStatsVO();
        vo.setTotalCost(BigDecimal.ZERO);
        when(messageStatsService.getCostStats(start, end)).thenReturn(vo);

        Result<CostStatsVO> result = messageStatsController.cost(start, end);

        assertTrue(result.isSuccess());
        verify(messageStatsService).getCostStats(start, end);
    }
}
