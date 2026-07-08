package com.njydsz.pmis.message.service;

import com.njydsz.pmis.message.dto.ChannelStatsVO;
import com.njydsz.pmis.message.dto.MessageStatsVO;
import com.njydsz.pmis.message.dto.ReceiptStatsVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息统计服务（P1-2 可观测看板）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MessageStatsService {

    /**
     * 发送总览统计。
     *
     * @param start 起始时间（含），null 则默认最近 24h
     * @param end   结束时间（含），null 则当前时间
     * @return 总览统计
     */
    MessageStatsVO getOverview(LocalDateTime start, LocalDateTime end);

    /**
     * 按通道维度的发送统计。
     *
     * @param start 起始时间（含）
     * @param end   结束时间（含）
     * @return 各通道统计列表
     */
    List<ChannelStatsVO> getChannelStats(LocalDateTime start, LocalDateTime end);

    /**
     * 回执统计。
     *
     * @param start 起始时间（含）
     * @param end   结束时间（含）
     * @return 回执统计
     */
    ReceiptStatsVO getReceiptStats(LocalDateTime start, LocalDateTime end);
}
