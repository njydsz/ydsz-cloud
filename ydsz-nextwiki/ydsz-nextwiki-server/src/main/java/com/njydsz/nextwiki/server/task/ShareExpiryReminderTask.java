package com.njydsz.nextwiki.server.task;

import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.service.ShareDomainService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分享链接到期提醒定时任务。
 *
 * <p>每小时扫描即将到期的分享链接（24 小时内到期），发布提醒事件。
 *
 * <p>实际通知（站内信/邮件/推送）由事件监听器处理，本任务仅负责识别与触发。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareExpiryReminderTask {

    /** 到期提醒提前小时数 */
    private static final int EXPIRY_REMINDER_HOURS = 24;

    private final ShareDomainService shareDomainService;

    /**
     * 扫描即将到期的分享链接并触发提醒。
     *
     * <p>每小时执行一次，查找 24 小时内即将过期且未发送过提醒的分享链接。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scanExpiringShares() {
        try {
            List<ShareLink> expiringShares =
                    shareDomainService.findExpiringShares(EXPIRY_REMINDER_HOURS);

            if (expiringShares == null || expiringShares.isEmpty()) {
                return;
            }

            log.info("[ShareExpiryReminder] 发现即将到期的分享链接: count={}",
                    expiringShares.size());

            for (ShareLink share : expiringShares) {
                // 标记提醒已发送（避免重复提醒）
                shareDomainService.markReminderSent(share.getId());
                log.info("[ShareExpiryReminder] 分享即将到期: shareId={}, shareCode={}, expireTime={}",
                        share.getId(), share.getShareCode(), share.getExpireTime());
                // TODO: 发布领域事件，由通知服务订阅后投递站内信/邮件
            }
        } catch (Exception e) {
            log.error("[ShareExpiryReminder] 扫描到期分享失败", e);
        }
    }
}
