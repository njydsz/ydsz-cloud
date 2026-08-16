package com.njydsz.message.server.service.chain.handler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.entity.config.MsgPreference;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.config.PreferenceService;
import com.njydsz.common.safe.sensitive.SensitiveUtil;

/**
 * 用户偏好校验 Handler（DND 免打扰时段）。
 *
 * <p>检查用户是否设置了免打扰时段，在 DND 时段内：
 * <ul>
 *   <li>非打扰型通道（如 URGENT）直接放行</li>
 *   <li>紧急消息绕过 DND</li>
 *   <li>智能定时开启时延迟到 DND 结束后发送</li>
 *   <li>延迟超过阈值则丢弃</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(500)
@RequiredArgsConstructor
public class PreferenceHandler implements SendHandler {

    private final PreferenceService preferenceService;
    private final MessageProperties messageProperties;
    private final MessageMetrics messageMetrics;

    @Override
    public boolean handle(MessageRequest request, SendContext ctx) {
        String receiver = ctx.getReceiver();
        String channel = ctx.getChannel();
        String bizType = ctx.getBizType();
        if (!StringUtils.hasText(receiver)) {
            return true;
        }
        MsgPreference pref = preferenceService.getByUser(receiver, channel, bizType);
        ctx.setPreference(pref);
        if (pref == null || !Integer.valueOf(1).equals(pref.getDndEnabled())) {
            return true;
        }
        if (!isInDndPeriod(pref)) {
            return true;
        }
        // 在 DND 时段内
        MessageProperties.SmartTimingConfig stc = messageProperties.getSmartTiming();
        boolean channelDisruptive = stc != null && stc.isDisruptive(channel);
        boolean urgentBypass = stc != null && stc.isUrgentBypassDnd()
                && "URGENT".equals(resolvePriority(request));
        if (!channelDisruptive) {
            log.debug("[Message] 非打扰型通道绕过 DND: channel={}", channel);
            return true;
        }
        if (urgentBypass) {
            log.info("[Message] URGENT 消息绕过 DND: receiver={} channel={}",
                    SensitiveUtil.scanAndMask(receiver), channel);
            return true;
        }
        if (stc != null && stc.isEnabled()) {
            LocalDateTime nextTime = calculateDndEndTime(pref);
            if (nextTime == null) {
                messageMetrics.recordSend(channel, "DND_SKIPPED", 0);
                ctx.setErrorResult(MessageResult.fail(channel, "当前为免打扰时段"));
                return false;
            }
            long deferHours = Duration.between(LocalDateTime.now(), nextTime).toHours();
            if (deferHours > stc.getMaxDeferHours()) {
                log.info("[Message] DND 延迟超过阈值,丢弃: receiver={} defer={}h max={}h",
                        SensitiveUtil.scanAndMask(receiver), deferHours, stc.getMaxDeferHours());
                messageMetrics.recordSend(channel, "DND_DROPPED", 0);
                ctx.setErrorResult(MessageResult.fail(channel, "免打扰时段消息延迟过久,已丢弃"));
                return false;
            }
            log.info("[Message] DND 延迟发送: receiver={} dnd={}~{} nextSendAt={}",
                    SensitiveUtil.scanAndMask(receiver), pref.getDndStart(), pref.getDndEnd(), nextTime);
            messageMetrics.recordSend(channel, "DND_DEFERRED", 0);
            request.setScheduledAt(nextTime);
            ctx.setScheduledAt(nextTime);
            return true;
        }
        messageMetrics.recordSend(channel, "DND_SKIPPED", 0);
        ctx.setErrorResult(MessageResult.fail(channel, "当前为免打扰时段"));
        return false;
    }

    @Override
    public int order() {
        return 500;
    }

    /**
     * 判断当前是否在 DND 免打扰时段。
     */
    private boolean isInDndPeriod(MsgPreference pref) {
        String start = pref.getDndStart();
        String end = pref.getDndEnd();
        if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return false;
        }
        try {
            LocalTime now = LocalTime.now();
            LocalTime s = LocalTime.parse(start);
            LocalTime e = LocalTime.parse(end);
            if (s.isBefore(e)) {
                return !now.isBefore(s) && now.isBefore(e);
            } else {
                return !now.isBefore(s) || now.isBefore(e);
            }
        } catch (Exception ex) {
            log.warn("[Message] DND 时段解析失败: start={} end={} err={}",
                    start, end, ex.getMessage());
            return false;
        }
    }

    /**
     * 计算 DND 结束时间（下次可发送时间）。
     */
    private LocalDateTime calculateDndEndTime(MsgPreference pref) {
        String startStr = pref.getDndStart();
        String endStr = pref.getDndEnd();
        if (!StringUtils.hasText(startStr) || !StringUtils.hasText(endStr)) {
            return null;
        }
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);
            LocalDateTime todayEnd = LocalDateTime.now().toLocalDate().atTime(end);
            LocalDateTime nextEnd;
            if (start.isBefore(end)) {
                nextEnd = todayEnd;
            } else {
                if (now.isBefore(end)) {
                    nextEnd = todayEnd;
                } else {
                    nextEnd = todayEnd.plusDays(1);
                }
            }
            MessageProperties.SmartTimingConfig stc = messageProperties.getSmartTiming();
            long buffer = (stc != null) ? stc.getDndBufferSeconds() : 0L;
            return nextEnd.plusSeconds(buffer);
        } catch (Exception e) {
            log.warn("[Message] DND 结束时间计算失败: start={} end={} err={}",
                    startStr, endStr, e.getMessage());
            return null;
        }
    }

    /**
     * 解析消息优先级。
     */
    private String resolvePriority(MessageRequest request) {
        return request.getPriority() != null ? request.getPriority() : "NORMAL";
    }
}
