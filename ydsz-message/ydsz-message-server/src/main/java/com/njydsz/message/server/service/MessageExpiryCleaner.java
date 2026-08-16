package com.njydsz.message.server.service.core;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.message.domain.entity.core.MsgNotification;
import com.njydsz.message.infra.mapper.core.MsgNotificationMapper;

/**
 * 消息过期清理器。
 * <p>定时清理过期/已读 N 个月的消息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Component
@RequiredArgsConstructor
public class MessageExpiryCleaner {

    private final MsgNotificationMapper msgNotificationMapper;

    /**
     * 每天凌晨 3 点执行过期清理。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @DistributedScheduled(lockKey = "message:expiry-clean")
    public void cleanExpiredNotifications() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int rows = msgNotificationMapper.update(null,
                    new LambdaUpdateWrapper<MsgNotification>()
                            .lt(MsgNotification::getExpiredAt, now)
                            .eq(MsgNotification::getDeleted, 0)
                            .set(MsgNotification::getDeleted, 1));
            log.info("[ExpiryCleaner] 清理过期通知: count={} threshold={}", rows, now);
        } catch (Exception e) {
            log.error("[ExpiryCleaner] 清理过期通知失败: {}", e.getMessage(), e);
        }
    }
}
