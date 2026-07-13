package com.njydsz.pmis.message.server.service.core;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.message.domain.entity.core.MsgNotificationDO;
import com.njydsz.pmis.message.infra.mapper.core.MsgNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 消息过期自动清理器（P1-7）。
 *
 * <p>定时扫描 expired_at 已过期的站内通知，将其标记为已删除（逻辑删除），
 * 避免收件箱累积大量过期消息影响查询性能。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
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
    public void cleanExpiredNotifications() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int rows = msgNotificationMapper.update(null,
                    new LambdaUpdateWrapper<MsgNotificationDO>()
                            .lt(MsgNotificationDO::getExpiredAt, now)
                            .eq(MsgNotificationDO::getDeleted, 0)
                            .set(MsgNotificationDO::getDeleted, 1));
            log.info("[ExpiryCleaner] 清理过期通知: count={} threshold={}", rows, now);
        } catch (Exception e) {
            log.error("[ExpiryCleaner] 清理过期通知失败: {}", e.getMessage(), e);
        }
    }
}
