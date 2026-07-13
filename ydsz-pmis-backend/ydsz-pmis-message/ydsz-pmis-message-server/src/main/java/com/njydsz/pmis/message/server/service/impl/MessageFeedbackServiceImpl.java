package com.njydsz.pmis.message.server.service.impl.core;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.domain.dto.core.MessageFeedbackDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgFeedbackDO;
import com.njydsz.pmis.message.infra.mapper.config.MsgFeedbackMapper;
import com.njydsz.pmis.message.server.service.core.MessageFeedbackService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P1-4: 消息质量反馈服务实现。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageFeedbackServiceImpl implements MessageFeedbackService {

    /** 消息反馈 Mapper */
    private final MsgFeedbackMapper msgFeedbackMapper;

    /** 降频判断窗口：最近多少条反馈 */
    private static final int FREQ_CHECK_WINDOW = 5;
    /** 降频阈值：平均分低于此值则建议降频 */
    private static final double FREQ_REDUCTION_THRESHOLD = 2.5;

    @Override
    public String submitFeedback(MessageFeedbackDTO dto) {
        if (dto == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "反馈内容不能为空");
        }
        if (!StringUtils.hasText(dto.getMsgId()) && !StringUtils.hasText(dto.getNotificationId())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "消息 ID 或通知 ID 不能为空");
        }
        if (!StringUtils.hasText(dto.getUserId())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "用户 ID 不能为空");
        }
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "评分必须在 1-5 之间");
        }

        MsgFeedbackDO feedback = new MsgFeedbackDO();
        feedback.setMsgId(dto.getMsgId());
        feedback.setNotificationId(dto.getNotificationId());
        feedback.setUserId(dto.getUserId());
        feedback.setRating(dto.getRating());
        feedback.setFeedbackType(dto.getFeedbackType());
        feedback.setContent(dto.getContent());
        feedback.setTenantId(TenantContext.getTenantId());

        // 通道和业务类型由前端或上游传入，此处不强制补全

        msgFeedbackMapper.insert(feedback);
        log.info("[Feedback] 用户反馈已提交: userId={} msgId={} rating={} type={}",
                dto.getUserId(), dto.getMsgId(), dto.getRating(), dto.getFeedbackType());
        return feedback.getId();
    }

    @Override
    public double getAverageRating(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        List<MsgFeedbackDO> feedbacks = msgFeedbackMapper.selectList(
                new LambdaQueryWrapper<MsgFeedbackDO>()
                        .eq(MsgFeedbackDO::getUserId, userId)
                        .orderByDesc(MsgFeedbackDO::getCreatedAt)
                        .last("LIMIT 100"));
        if (feedbacks.isEmpty()) {
            return 0;
        }
        return feedbacks.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(MsgFeedbackDO::getRating)
                .average()
                .orElse(0);
    }

    @Override
    public double getAverageRatingByChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return 0;
        }
        List<MsgFeedbackDO> feedbacks = msgFeedbackMapper.selectList(
                new LambdaQueryWrapper<MsgFeedbackDO>()
                        .eq(MsgFeedbackDO::getChannel, channel)
                        .orderByDesc(MsgFeedbackDO::getCreatedAt)
                        .last("LIMIT 1000"));
        if (feedbacks.isEmpty()) {
            return 0;
        }
        return feedbacks.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(MsgFeedbackDO::getRating)
                .average()
                .orElse(0);
    }

    @Override
    public Page<MsgFeedbackDO> pageFeedback(int page, int size, String channel, String userId) {
        Page<MsgFeedbackDO> p = new Page<>(page, size);
        LambdaQueryWrapper<MsgFeedbackDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(channel)) {
            wrapper.eq(MsgFeedbackDO::getChannel, channel);
        }
        if (StringUtils.hasText(userId)) {
            wrapper.eq(MsgFeedbackDO::getUserId, userId);
        }
        wrapper.orderByDesc(MsgFeedbackDO::getCreatedAt);
        return msgFeedbackMapper.selectPage(p, wrapper);
    }

    @Override
    public boolean shouldReduceFrequency(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        List<MsgFeedbackDO> recentFeedbacks = msgFeedbackMapper.selectList(
                new LambdaQueryWrapper<MsgFeedbackDO>()
                        .eq(MsgFeedbackDO::getUserId, userId)
                        .orderByDesc(MsgFeedbackDO::getCreatedAt)
                        .last("LIMIT " + FREQ_CHECK_WINDOW));
        if (recentFeedbacks.size() < FREQ_CHECK_WINDOW) {
            return false; // 反馈不足，不降频
        }
        double avgRating = recentFeedbacks.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(MsgFeedbackDO::getRating)
                .average()
                .orElse(5.0);
        boolean shouldReduce = avgRating < FREQ_REDUCTION_THRESHOLD;
        if (shouldReduce) {
            log.info("[Feedback] 用户建议降频: userId={} avgRating={} threshold={}",
                    userId, avgRating, FREQ_REDUCTION_THRESHOLD);
        }
        return shouldReduce;
    }
}
