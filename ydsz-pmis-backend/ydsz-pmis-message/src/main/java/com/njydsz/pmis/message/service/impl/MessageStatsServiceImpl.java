package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.dto.ChannelStatsVO;
import com.njydsz.pmis.message.dto.CostStatsVO;
import com.njydsz.pmis.message.dto.FunnelStatsVO;
import com.njydsz.pmis.message.dto.MessageStatsVO;
import com.njydsz.pmis.message.dto.ReceiptStatsVO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageChannelEnum;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.enums.ReceiptStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.service.MessageStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 消息统计服务实现（P1-2 可观测看板）。
 *
 * <p>基于 {@code pmis_msg_log} 表的 selectCount 聚合查询,提供发送总览 / 通道维度 / 回执统计。
 * 查询结果均带时间范围过滤（created_at 区间）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStatsServiceImpl implements MessageStatsService {

    private final MsgLogMapper msgLogMapper;
    private final MessageProperties messageProperties;

    @Override
    public MessageStatsVO getOverview(LocalDateTime start, LocalDateTime end) {
        LocalDateTime[] range = normalizeRange(start, end);
        LocalDateTime actualStart = range[0];
        LocalDateTime actualEnd = range[1];

        long success = countByStatus(MessageStatusEnum.SUCCESS, actualStart, actualEnd);
        long failed = countByStatus(MessageStatusEnum.FAILED, actualStart, actualEnd);
        long retry = countByStatus(MessageStatusEnum.RETRY, actualStart, actualEnd);
        long dead = countByStatus(MessageStatusEnum.DEAD, actualStart, actualEnd);
        long recalled = countByStatus(MessageStatusEnum.RECALLED, actualStart, actualEnd);
        long total = success + failed + retry + dead + recalled;

        MessageStatsVO vo = new MessageStatsVO();
        vo.setTotal(total);
        vo.setSuccess(success);
        vo.setFailed(failed);
        vo.setRetry(retry);
        vo.setDead(dead);
        vo.setRecalled(recalled);
        vo.setSuccessRate(total > 0 ? round2(success * 100.0 / total) : 0.0);
        vo.setDeadRate(total > 0 ? round2(dead * 100.0 / total) : 0.0);
        vo.setStart(actualStart.toString());
        vo.setEnd(actualEnd.toString());
        return vo;
    }

    @Override
    public List<ChannelStatsVO> getChannelStats(LocalDateTime start, LocalDateTime end) {
        LocalDateTime[] range = normalizeRange(start, end);
        LocalDateTime actualStart = range[0];
        LocalDateTime actualEnd = range[1];

        List<ChannelStatsVO> result = new ArrayList<>();
        for (MessageChannelEnum ch : MessageChannelEnum.values()) {
            String channel = ch.name();
            long success = countByStatusAndChannel(MessageStatusEnum.SUCCESS, channel, actualStart, actualEnd);
            long failed = countByStatusAndChannel(MessageStatusEnum.FAILED, channel, actualStart, actualEnd);
            long retry = countByStatusAndChannel(MessageStatusEnum.RETRY, channel, actualStart, actualEnd);
            long dead = countByStatusAndChannel(MessageStatusEnum.DEAD, channel, actualStart, actualEnd);
            long total = success + failed + retry + dead;

            // 只输出有数据的通道
            if (total == 0) {
                continue;
            }

            ChannelStatsVO vo = new ChannelStatsVO();
            vo.setChannel(channel);
            vo.setTotal(total);
            vo.setSuccess(success);
            vo.setFailed(failed);
            vo.setRetry(retry);
            vo.setDead(dead);
            vo.setSuccessRate(total > 0 ? round2(success * 100.0 / total) : 0.0);
            vo.setDeadRate(total > 0 ? round2(dead * 100.0 / total) : 0.0);
            result.add(vo);
        }
        return result;
    }

    @Override
    public ReceiptStatsVO getReceiptStats(LocalDateTime start, LocalDateTime end) {
        LocalDateTime[] range = normalizeRange(start, end);
        LocalDateTime actualStart = range[0];
        LocalDateTime actualEnd = range[1];

        // 回执分母 = 成功发送数
        long total = countByStatus(MessageStatusEnum.SUCCESS, actualStart, actualEnd);
        long delivered = countByReceiptStatus(ReceiptStatusEnum.DELIVERED, actualStart, actualEnd);
        long read = countByReceiptStatus(ReceiptStatusEnum.READ, actualStart, actualEnd);
        long clicked = countByReceiptStatus(ReceiptStatusEnum.CLICKED, actualStart, actualEnd);
        long failed = countByReceiptStatus(ReceiptStatusEnum.FAILED, actualStart, actualEnd);
        long timeout = countByReceiptStatus(ReceiptStatusEnum.TIMEOUT, actualStart, actualEnd);
        long none = countByReceiptStatus(ReceiptStatusEnum.NONE, actualStart, actualEnd);

        ReceiptStatsVO vo = new ReceiptStatsVO();
        vo.setTotal(total);
        vo.setDelivered(delivered);
        vo.setRead(read);
        vo.setClicked(clicked);
        vo.setFailed(failed);
        vo.setTimeout(timeout);
        vo.setNone(none);
        vo.setDeliveryRate(total > 0 ? round2((delivered + read + clicked) * 100.0 / total) : 0.0);
        vo.setReadRate(total > 0 ? round2((read + clicked) * 100.0 / total) : 0.0);
        return vo;
    }

    /**
     * 按状态统计数量（带时间范围）。
     */
    private long countByStatus(MessageStatusEnum status, LocalDateTime start, LocalDateTime end) {
        Long count = msgLogMapper.selectCount(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getStatus, status.name())
                .ge(MsgLogDO::getCreatedAt, start)
                .le(MsgLogDO::getCreatedAt, end));
        return count == null ? 0L : count;
    }

    /**
     * 按状态 + 通道统计数量（带时间范围）。
     */
    private long countByStatusAndChannel(MessageStatusEnum status, String channel,
                                         LocalDateTime start, LocalDateTime end) {
        Long count = msgLogMapper.selectCount(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getStatus, status.name())
                .eq(MsgLogDO::getChannel, channel)
                .ge(MsgLogDO::getCreatedAt, start)
                .le(MsgLogDO::getCreatedAt, end));
        return count == null ? 0L : count;
    }

    /**
     * 按回执状态统计数量（带时间范围）。
     */
    private long countByReceiptStatus(ReceiptStatusEnum status, LocalDateTime start, LocalDateTime end) {
        Long count = msgLogMapper.selectCount(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getReceiptStatus, status.name())
                .ge(MsgLogDO::getCreatedAt, start)
                .le(MsgLogDO::getCreatedAt, end));
        return count == null ? 0L : count;
    }

    @Override
    public FunnelStatsVO getFunnel(LocalDateTime start, LocalDateTime end, String channel, String templateCode) {
        LocalDateTime[] range = normalizeRange(start, end);
        LocalDateTime actualStart = range[0];
        LocalDateTime actualEnd = range[1];

        // 漏斗第1层：已发送 = status = SUCCESS
        long sent = countForFunnel(MessageStatusEnum.SUCCESS.name(), null, channel, templateCode,
                actualStart, actualEnd);
        // 漏斗第2层：已送达 = receiptStatus IN (DELIVERED, READ, CLICKED)（累积）
        long delivered = countForFunnel(null,
                java.util.Arrays.asList(ReceiptStatusEnum.DELIVERED.name(),
                        ReceiptStatusEnum.READ.name(), ReceiptStatusEnum.CLICKED.name()),
                channel, templateCode, actualStart, actualEnd);
        // 漏斗第3层：已读 = receiptStatus IN (READ, CLICKED)（累积）
        long read = countForFunnel(null,
                java.util.Arrays.asList(ReceiptStatusEnum.READ.name(),
                        ReceiptStatusEnum.CLICKED.name()),
                channel, templateCode, actualStart, actualEnd);
        // 漏斗第4层：已点击 = receiptStatus = CLICKED
        long clicked = countForFunnel(null,
                java.util.Collections.singletonList(ReceiptStatusEnum.CLICKED.name()),
                channel, templateCode, actualStart, actualEnd);

        FunnelStatsVO vo = new FunnelStatsVO();
        vo.setSent(sent);
        vo.setDelivered(delivered);
        vo.setRead(read);
        vo.setClicked(clicked);
        vo.setDeliveryRate(sent > 0 ? round2(delivered * 100.0 / sent) : 0.0);
        vo.setReadRate(sent > 0 ? round2(read * 100.0 / sent) : 0.0);
        vo.setClickRate(sent > 0 ? round2(clicked * 100.0 / sent) : 0.0);
        vo.setDeliveredToReadRate(delivered > 0 ? round2(read * 100.0 / delivered) : 0.0);
        vo.setReadToClickRate(read > 0 ? round2(clicked * 100.0 / read) : 0.0);
        vo.setOverallConversionRate(sent > 0 ? round2(clicked * 100.0 / sent) : 0.0);
        vo.setChannel(channel);
        vo.setTemplateCode(templateCode);
        vo.setStart(actualStart.toString());
        vo.setEnd(actualEnd.toString());
        return vo;
    }

    @Override
    public CostStatsVO getCostStats(LocalDateTime start, LocalDateTime end) {
        LocalDateTime[] range = normalizeRange(start, end);
        LocalDateTime actualStart = range[0];
        LocalDateTime actualEnd = range[1];

        MessageProperties.CostConfig costCfg = messageProperties.getCost();
        Map<String, BigDecimal> unitPrices = costCfg != null && costCfg.getUnitPrices() != null
                ? costCfg.getUnitPrices() : java.util.Collections.emptyMap();

        List<CostStatsVO.ChannelCost> channelCosts = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : unitPrices.entrySet()) {
            String channel = entry.getKey();
            BigDecimal unitPrice = entry.getValue();
            // 统计该通道 SUCCESS 消息数
            LambdaQueryWrapper<MsgLogDO> w = new LambdaQueryWrapper<>();
            w.eq(MsgLogDO::getChannel, channel);
            w.eq(MsgLogDO::getStatus, MessageStatusEnum.SUCCESS.name());
            w.ge(MsgLogDO::getCreatedAt, actualStart);
            w.le(MsgLogDO::getCreatedAt, actualEnd);
            Long count = msgLogMapper.selectCount(w);
            long msgCount = count == null ? 0L : count;

            BigDecimal channelCost = unitPrice.multiply(BigDecimal.valueOf(msgCount));

            CostStatsVO.ChannelCost cc = new CostStatsVO.ChannelCost();
            cc.setChannel(channel);
            cc.setMessageCount(msgCount);
            cc.setUnitPrice(unitPrice);
            cc.setTotalCost(channelCost);
            channelCosts.add(cc);
            totalCost = totalCost.add(channelCost);
        }

        CostStatsVO vo = new CostStatsVO();
        vo.setTotalCost(totalCost);
        vo.setChannels(channelCosts);
        vo.setStart(actualStart.toString());
        vo.setEnd(actualEnd.toString());
        return vo;
    }

    /**
     * P2-2: 漏斗通用计数查询。
     *
     * <p>按 status（精确）或 receiptStatus（IN 集合）过滤,同时支持可选的 channel / templateCode 维度过滤。
     * status 与 receiptStatusList 互斥：status 非空时按 status 查,否则按 receiptStatusList 查。
     *
     * @param status            发送状态（非空时按此过滤）
     * @param receiptStatusList 回执状态集合（status 为空时按此 IN 过滤）
     * @param channel           通道过滤（可选）
     * @param templateCode      模板编码过滤（可选）
     * @param start             起始时间
     * @param end               结束时间
     * @return 计数
     */
    private long countForFunnel(String status, java.util.List<String> receiptStatusList,
                                 String channel, String templateCode,
                                 LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<MsgLogDO> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(MsgLogDO::getStatus, status);
        } else if (receiptStatusList != null && !receiptStatusList.isEmpty()) {
            w.in(MsgLogDO::getReceiptStatus, receiptStatusList);
        }
        if (channel != null && !channel.isBlank()) {
            w.eq(MsgLogDO::getChannel, channel);
        }
        if (templateCode != null && !templateCode.isBlank()) {
            w.eq(MsgLogDO::getTemplateCode, templateCode);
        }
        w.ge(MsgLogDO::getCreatedAt, start);
        w.le(MsgLogDO::getCreatedAt, end);
        Long count = msgLogMapper.selectCount(w);
        return count == null ? 0L : count;
    }

    /**
     * 规范化时间范围：start 为 null 时取 24h 前，end 为 null 时取当前时间。
     */
    private LocalDateTime[] normalizeRange(LocalDateTime start, LocalDateTime end) {
        LocalDateTime actualEnd = end != null ? end : LocalDateTime.now();
        LocalDateTime actualStart = start != null ? start : actualEnd.minusHours(24);
        return new LocalDateTime[]{actualStart, actualEnd};
    }

    /**
     * 保留两位小数。
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
