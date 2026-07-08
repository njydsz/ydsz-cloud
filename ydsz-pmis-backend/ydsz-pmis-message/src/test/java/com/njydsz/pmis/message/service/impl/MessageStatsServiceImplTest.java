package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.message.dto.ChannelStatsVO;
import com.njydsz.pmis.message.dto.MessageStatsVO;
import com.njydsz.pmis.message.dto.ReceiptStatsVO;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link MessageStatsServiceImpl} 单元测试（P1-2 可观测看板）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageStatsServiceImpl 统计服务测试")
@ExtendWith(MockitoExtension.class)
class MessageStatsServiceImplTest {

    @Mock
    private MsgLogMapper msgLogMapper;

    @InjectMocks
    private MessageStatsServiceImpl messageStatsService;

    @Test
    @DisplayName("getOverview 正确聚合各状态计数与比率")
    void getOverviewShouldAggregateCountsAndRates() {
        // selectCount 调用顺序: SUCCESS, FAILED, RETRY, DEAD, RECALLED
        when(msgLogMapper.selectCount(any())).thenReturn(100L, 10L, 5L, 3L, 2L);

        LocalDateTime start = LocalDateTime.now().minusHours(24);
        LocalDateTime end = LocalDateTime.now();
        MessageStatsVO vo = messageStatsService.getOverview(start, end);

        assertEquals(120L, vo.getTotal());
        assertEquals(100L, vo.getSuccess());
        assertEquals(10L, vo.getFailed());
        assertEquals(5L, vo.getRetry());
        assertEquals(3L, vo.getDead());
        assertEquals(2L, vo.getRecalled());
        // 100/120*100 = 83.33
        assertEquals(83.33, vo.getSuccessRate(), 0.01);
        // 3/120*100 = 2.5
        assertEquals(2.5, vo.getDeadRate(), 0.01);
        assertNotNull(vo.getStart());
        assertNotNull(vo.getEnd());
    }

    @Test
    @DisplayName("getOverview total=0 时比率为 0 不抛异常")
    void getOverviewShouldReturnZeroRatesWhenNoData() {
        when(msgLogMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L, 0L);

        MessageStatsVO vo = messageStatsService.getOverview(null, null);

        assertEquals(0L, vo.getTotal());
        assertEquals(0.0, vo.getSuccessRate());
        assertEquals(0.0, vo.getDeadRate());
    }

    @Test
    @DisplayName("getOverview null 时间参数时默认最近 24h")
    void getOverviewShouldDefaultToLast24hWhenNullParams() {
        when(msgLogMapper.selectCount(any())).thenReturn(1L, 0L, 0L, 0L, 0L);

        MessageStatsVO vo = messageStatsService.getOverview(null, null);

        assertEquals(1L, vo.getTotal());
        assertNotNull(vo.getStart());
        assertNotNull(vo.getEnd());
    }

    @Test
    @DisplayName("getChannelStats 只返回有数据的通道")
    void getChannelStatsShouldOnlyReturnChannelsWithData() {
        // 8 通道 × 4 状态 = 32 次 selectCount
        // SMS: success=50, failed=5, retry=2, dead=1 → total=58, 有数据
        // EMAIL~FEISHU: 全 0 → 跳过
        when(msgLogMapper.selectCount(any())).thenReturn(
                50L, 5L, 2L, 1L,       // SMS
                0L, 0L, 0L, 0L,        // EMAIL
                0L, 0L, 0L, 0L,        // PUSH
                0L, 0L, 0L, 0L,        // IN_APP
                0L, 0L, 0L, 0L,        // WEBHOOK
                0L, 0L, 0L, 0L,        // DINGTALK
                0L, 0L, 0L, 0L,        // WECOM
                0L, 0L, 0L, 0L         // FEISHU
        );

        List<ChannelStatsVO> result = messageStatsService.getChannelStats(null, null);

        assertEquals(1, result.size());
        ChannelStatsVO sms = result.get(0);
        assertEquals("SMS", sms.getChannel());
        assertEquals(58L, sms.getTotal());
        assertEquals(50L, sms.getSuccess());
        assertEquals(5L, sms.getFailed());
        assertEquals(2L, sms.getRetry());
        assertEquals(1L, sms.getDead());
        // 50/58*100 = 86.21
        assertEquals(86.21, sms.getSuccessRate(), 0.01);
    }

    @Test
    @DisplayName("getChannelStats 所有通道无数据时返回空列表")
    void getChannelStatsShouldReturnEmptyWhenNoData() {
        when(msgLogMapper.selectCount(any())).thenReturn(0L);

        List<ChannelStatsVO> result = messageStatsService.getChannelStats(null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getReceiptStats 正确计算送达率与已读率")
    void getReceiptStatsShouldCalculateRatesCorrectly() {
        // selectCount 调用顺序: SUCCESS(total), DELIVERED, READ, CLICKED, FAILED, TIMEOUT, NONE
        when(msgLogMapper.selectCount(any())).thenReturn(100L, 50L, 30L, 10L, 5L, 3L, 2L);

        ReceiptStatsVO vo = messageStatsService.getReceiptStats(null, null);

        assertEquals(100L, vo.getTotal());
        assertEquals(50L, vo.getDelivered());
        assertEquals(30L, vo.getRead());
        assertEquals(10L, vo.getClicked());
        assertEquals(5L, vo.getFailed());
        assertEquals(3L, vo.getTimeout());
        assertEquals(2L, vo.getNone());
        // (50+30+10)/100*100 = 90.0
        assertEquals(90.0, vo.getDeliveryRate(), 0.01);
        // (30+10)/100*100 = 40.0
        assertEquals(40.0, vo.getReadRate(), 0.01);
    }

    @Test
    @DisplayName("getReceiptStats total=0 时比率为 0")
    void getReceiptStatsShouldReturnZeroRatesWhenNoSuccess() {
        when(msgLogMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L, 0L, 0L, 0L);

        ReceiptStatsVO vo = messageStatsService.getReceiptStats(null, null);

        assertEquals(0L, vo.getTotal());
        assertEquals(0.0, vo.getDeliveryRate());
        assertEquals(0.0, vo.getReadRate());
    }
}
