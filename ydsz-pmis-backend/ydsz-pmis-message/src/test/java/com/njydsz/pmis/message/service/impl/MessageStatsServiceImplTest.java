package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.dto.ChannelStatsVO;
import com.njydsz.pmis.message.dto.CostStatsVO;
import com.njydsz.pmis.message.dto.FunnelStatsVO;
import com.njydsz.pmis.message.dto.MessageStatsVO;
import com.njydsz.pmis.message.dto.ReceiptStatsVO;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private MessageProperties messageProperties;

    @InjectMocks
    private MessageStatsServiceImpl messageStatsService;

    /**
     * P2-4: 为成本统计测试准备默认单价配置。
     *
     * <p>使用 lenient 模式,因为非成本相关测试不会调用 messageProperties.getCost()。
     */
    @BeforeEach
    void setUpCostConfig() {
        MessageProperties.CostConfig costCfg = new MessageProperties.CostConfig();
        lenient().when(messageProperties.getCost()).thenReturn(costCfg);
    }

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

    // ============ P2-2: 漏斗分析测试 ============

    @Test
    @DisplayName("getFunnel 正确计算漏斗各阶段与转化率")
    void getFunnelShouldCalculateStagesAndRates() {
        // selectCount 调用顺序: sent, delivered, read, clicked
        when(msgLogMapper.selectCount(any())).thenReturn(100L, 80L, 50L, 20L);

        FunnelStatsVO vo = messageStatsService.getFunnel(null, null, null, null);

        assertEquals(100L, vo.getSent(), "已发送 = 100");
        assertEquals(80L, vo.getDelivered(), "已送达 = 80");
        assertEquals(50L, vo.getRead(), "已读 = 50");
        assertEquals(20L, vo.getClicked(), "已点击 = 20");
        // 80/100*100 = 80.0
        assertEquals(80.0, vo.getDeliveryRate(), 0.01, "送达率");
        // 50/100*100 = 50.0
        assertEquals(50.0, vo.getReadRate(), 0.01, "已读率");
        // 20/100*100 = 20.0
        assertEquals(20.0, vo.getClickRate(), 0.01, "点击率");
        // 50/80*100 = 62.5
        assertEquals(62.5, vo.getDeliveredToReadRate(), 0.01, "送达→已读转化率");
        // 20/50*100 = 40.0
        assertEquals(40.0, vo.getReadToClickRate(), 0.01, "已读→点击转化率");
        // 20/100*100 = 20.0
        assertEquals(20.0, vo.getOverallConversionRate(), 0.01, "整体转化率");
    }

    @Test
    @DisplayName("getFunnel sent=0 时所有比率为 0")
    void getFunnelShouldReturnZeroRatesWhenNoSent() {
        when(msgLogMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L);

        FunnelStatsVO vo = messageStatsService.getFunnel(null, null, null, null);

        assertEquals(0L, vo.getSent());
        assertEquals(0.0, vo.getDeliveryRate());
        assertEquals(0.0, vo.getOverallConversionRate());
    }

    @Test
    @DisplayName("getFunnel delivered=0 时 deliveredToReadRate 为 0(不除零)")
    void getFunnelShouldNotDivideByZeroWhenDeliveredIsZero() {
        when(msgLogMapper.selectCount(any())).thenReturn(100L, 0L, 0L, 0L);

        FunnelStatsVO vo = messageStatsService.getFunnel(null, null, null, null);

        assertEquals(100L, vo.getSent());
        assertEquals(0L, vo.getDelivered());
        assertEquals(0.0, vo.getDeliveredToReadRate(), "delivered=0 时 deliveredToReadRate 应为 0");
    }

    @Test
    @DisplayName("getFunnel read=0 时 readToClickRate 为 0(不除零)")
    void getFunnelShouldNotDivideByZeroWhenReadIsZero() {
        when(msgLogMapper.selectCount(any())).thenReturn(100L, 80L, 0L, 0L);

        FunnelStatsVO vo = messageStatsService.getFunnel(null, null, null, null);

        assertEquals(0.0, vo.getReadToClickRate(), "read=0 时 readToClickRate 应为 0");
    }

    @Test
    @DisplayName("getFunnel 带通道和模板过滤参数")
    void getFunnelShouldAcceptChannelAndTemplateFilters() {
        when(msgLogMapper.selectCount(any())).thenReturn(50L, 40L, 30L, 10L);

        FunnelStatsVO vo = messageStatsService.getFunnel(null, null, "SMS", "TPL_ALERT");

        assertEquals(50L, vo.getSent());
        assertEquals("SMS", vo.getChannel());
        assertEquals("TPL_ALERT", vo.getTemplateCode());
        // 10/50*100 = 20.0
        assertEquals(20.0, vo.getOverallConversionRate(), 0.01);
    }

    @Test
    @DisplayName("getFunnel null 时间参数时默认最近 24h")
    void getFunnelShouldDefaultToLast24hWhenNullParams() {
        when(msgLogMapper.selectCount(any())).thenReturn(1L, 1L, 1L, 1L);

        FunnelStatsVO vo = messageStatsService.getFunnel(null, null, null, null);

        assertEquals(1L, vo.getSent());
        assertNotNull(vo.getStart());
        assertNotNull(vo.getEnd());
    }

    // ============ P2-4: 成本看板测试 ============

    @Test
    @DisplayName("getCostStats 正确按通道单价计算总成本")
    void getCostStatsShouldCalculateCostByChannelUnitPrice() {
        // 默认 8 通道(LinkedHashMap 保证顺序),selectCount 调用顺序:
        // SMS, EMAIL, PUSH, IN_APP, WEBHOOK, DINGTALK, WECOM, FEISHU
        when(msgLogMapper.selectCount(any())).thenReturn(
                100L,  // SMS × 0.045 = 4.50
                200L,  // EMAIL × 0.001 = 0.20
                1000L, // PUSH × 0.0001 = 0.10
                50L,   // IN_APP × 0 = 0
                10L,   // WEBHOOK × 0 = 0
                0L,    // DINGTALK × 0 = 0
                0L,    // WECOM × 0 = 0
                0L     // FEISHU × 0 = 0
        );

        CostStatsVO vo = messageStatsService.getCostStats(null, null);

        // 4.50 + 0.20 + 0.10 = 4.80 (用 compareTo 避免 scale 差异)
        assertEquals(0, new BigDecimal("4.80").compareTo(vo.getTotalCost()),
                "总成本 = 4.80");
        assertEquals(8, vo.getChannels().size());

        // 校验 SMS 通道明细
        CostStatsVO.ChannelCost sms = vo.getChannels().stream()
                .filter(c -> "SMS".equals(c.getChannel())).findFirst().orElseThrow();
        assertEquals(100L, sms.getMessageCount());
        assertEquals(0, new BigDecimal("0.0450").compareTo(sms.getUnitPrice()));
        assertEquals(0, new BigDecimal("4.500").compareTo(sms.getTotalCost()));

        // 校验 EMAIL 通道明细
        CostStatsVO.ChannelCost email = vo.getChannels().stream()
                .filter(c -> "EMAIL".equals(c.getChannel())).findFirst().orElseThrow();
        assertEquals(200L, email.getMessageCount());
        assertEquals(0, new BigDecimal("0.0010").compareTo(email.getUnitPrice()));
        assertEquals(0, new BigDecimal("0.200").compareTo(email.getTotalCost()));

        // 校验 PUSH 通道明细
        CostStatsVO.ChannelCost push = vo.getChannels().stream()
                .filter(c -> "PUSH".equals(c.getChannel())).findFirst().orElseThrow();
        assertEquals(1000L, push.getMessageCount());
        assertEquals(0, new BigDecimal("0.0001").compareTo(push.getUnitPrice()));
        assertEquals(0, new BigDecimal("0.100").compareTo(push.getTotalCost()));

        assertNotNull(vo.getStart());
        assertNotNull(vo.getEnd());
    }

    @Test
    @DisplayName("getCostStats 无消息时总成本为 0")
    void getCostStatsShouldReturnZeroCostWhenNoMessages() {
        when(msgLogMapper.selectCount(any())).thenReturn(0L);

        CostStatsVO vo = messageStatsService.getCostStats(null, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getTotalCost()));
        assertEquals(8, vo.getChannels().size());
        // 每个通道 messageCount = 0,totalCost = 0
        assertTrue(vo.getChannels().stream().allMatch(c -> c.getMessageCount() == 0L));
        assertTrue(vo.getChannels().stream().allMatch(c -> c.getTotalCost().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    @DisplayName("getCostStats null 时间参数时默认最近 24h")
    void getCostStatsShouldDefaultToLast24hWhenNullParams() {
        when(msgLogMapper.selectCount(any())).thenReturn(0L);

        CostStatsVO vo = messageStatsService.getCostStats(null, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getTotalCost()));
        assertNotNull(vo.getStart());
        assertNotNull(vo.getEnd());
    }

    @Test
    @DisplayName("getCostStats 带时间参数正确传递")
    void getCostStatsShouldPassTimeParams() {
        when(msgLogMapper.selectCount(any())).thenReturn(0L);
        LocalDateTime start = LocalDateTime.now().minusHours(12);
        LocalDateTime end = LocalDateTime.now();

        CostStatsVO vo = messageStatsService.getCostStats(start, end);

        assertEquals(start.toString(), vo.getStart());
        assertEquals(end.toString(), vo.getEnd());
    }

    @Test
    @DisplayName("getCostStats unitPrices 为空时返回 0 成本")
    void getCostStatsShouldReturnZeroWhenUnitPricesEmpty() {
        MessageProperties.CostConfig emptyCfg = new MessageProperties.CostConfig();
        emptyCfg.setUnitPrices(new HashMap<>());
        when(messageProperties.getCost()).thenReturn(emptyCfg);

        CostStatsVO vo = messageStatsService.getCostStats(null, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getTotalCost()));
        assertTrue(vo.getChannels().isEmpty());
    }
}
