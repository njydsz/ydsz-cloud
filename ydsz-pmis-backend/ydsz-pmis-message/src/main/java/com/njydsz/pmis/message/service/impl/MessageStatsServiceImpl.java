package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.dto.ChannelStatsVO;
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
import java.util.List;

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
