package com.njydsz.message.server.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.message.domain.entity.core.MsgLogDO;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨通道转化归因服务（P2-1）。
 *
 * <p>追踪同一 bizId 在多通道的发送/回执链路,计算每个通道的转化率：
 * <ul>
 *   <li>发送数 → 送达率 → 已读率 → 点击率</li>
 *   <li>按通道维度汇总,识别最优触达通道</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelAttributionService {

    private final MsgLogMapper msgLogMapper;

    /**
     * 按 bizId 查询跨通道发送链路。
     *
     * @param bizId 业务单据 ID
     * @return 发送日志列表（含多通道）
     */
    public List<MsgLogDO> traceByBizId(String bizId) {
        if (bizId == null || bizId.isBlank()) {
            return List.of();
        }
        return msgLogMapper.selectList(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getBizId, bizId)
                .orderByAsc(MsgLogDO::getCreatedAt));
    }

    /**
     * 计算指定时间范围内各通道的转化漏斗。
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 通道转化统计列表
     */
    public List<ChannelFunnelStats> calculateFunnel(LocalDateTime startTime, LocalDateTime endTime) {
        List<MsgLogDO> logs = msgLogMapper.selectList(new LambdaQueryWrapper<MsgLogDO>()
                .ge(startTime != null, MsgLogDO::getCreatedAt, startTime)
                .le(endTime != null, MsgLogDO::getCreatedAt, endTime));

        Map<String, ChannelFunnelStats> statsMap = new LinkedHashMap<>();
        for (MsgLogDO log : logs) {
            String channel = log.getChannel();
            if (channel == null) continue;
            ChannelFunnelStats stats = statsMap.computeIfAbsent(channel, k -> {
                ChannelFunnelStats s = new ChannelFunnelStats();
                s.setChannel(k);
                return s;
            });
            stats.setTotal(stats.getTotal() + 1);
            if ("SUCCESS".equals(log.getStatus())) {
                stats.setDelivered(stats.getDelivered() + 1);
            }
            if ("READ".equals(log.getReceiptStatus()) || "CLICKED".equals(log.getReceiptStatus())) {
                stats.setRead(stats.getRead() + 1);
            }
            if ("CLICKED".equals(log.getReceiptStatus())) {
                stats.setClicked(stats.getClicked() + 1);
            }
        }

        List<ChannelFunnelStats> result = new ArrayList<>(statsMap.values());
        result.forEach(s -> {
            s.setDeliveryRate(s.getTotal() > 0 ? (double) s.getDelivered() / s.getTotal() : 0);
            s.setReadRate(s.getDelivered() > 0 ? (double) s.getRead() / s.getDelivered() : 0);
            s.setClickRate(s.getRead() > 0 ? (double) s.getClicked() / s.getRead() : 0);
        });
        return result;
    }

    /**
     * 通道转化漏斗统计。
     */
    @Data
    public static class ChannelFunnelStats {
        private String channel;
        private int total;
        private int delivered;
        private int read;
        private int clicked;
        private double deliveryRate;
        private double readRate;
        private double clickRate;
    }
}
