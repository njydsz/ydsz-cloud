package com.njydsz.nextwiki.server.task;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.repository.ShareLinkRepository;
import com.njydsz.nextwiki.domain.service.ShareLinkDomainService;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;

/**
 * 分享链接到期提醒定时任务。
 *
 * <p>每小时扫描即将到期的分享链接（24 小时内到期），通过 NotifyHelper 向分享创建者发送站内信提醒。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareExpiryReminderTask {

  /** 到期提醒提前小时数 */
  private static final int EXPIRY_REMINDER_HOURS = 24;

  private final ShareLinkDomainService shareLinkDomainService;
  private final ShareLinkRepository shareLinkRepository;
  private final NextwikiStructMapper mapper;
  /** 统一通知辅助类（到期提醒站内信） */
  private final NotifyHelper notifyHelper;

  /**
   * 扫描即将到期的分享链接并触发提醒。
   *
   * <p>每小时执行一次，查找 24 小时内即将过期且未发送过提醒的分享链接。
   */
  @DistributedScheduled(lockKey = "nextwiki:share-expiry-reminder", leaseTime = 300)
  @Scheduled(cron = "0 0 * * * *")
  public void scanExpiringShares() {
    try {
      // 查询即将到期的分享链接
      List<ShareLinkVO> expiringVOs = shareLinkRepository.findExpiringShares(EXPIRY_REMINDER_HOURS);

      if (expiringVOs == null || expiringVOs.isEmpty()) {
        return;
      }

      // 转换为 DTO 并调用领域服务过滤（仅 Active 状态 + 未发送提醒）
      List<ShareLinkDTO> expiringDTOs = mapper.shareLinkListVOToDTO(expiringVOs);
      List<ShareLinkDTO> toRemind = shareLinkDomainService.findExpiringShares(expiringDTOs, EXPIRY_REMINDER_HOURS);

      if (toRemind.isEmpty()) {
        return;
      }

      log.info("[ShareExpiryReminder] 发现即将到期的分享链接: count={}", toRemind.size());

      for (ShareLinkDTO share : toRemind) {
        // 标记提醒已发送（通过领域服务修改状态，然后持久化）
        shareLinkDomainService.markReminderSent(share);
        shareLinkRepository.update(share);

        // 向分享创建者发送到期提醒站内信（YDIZ-NOTIFY 规范：推送统一走 NotifyHelper）
        notifyExpiryReminder(share);

        log.info(
            "[ShareExpiryReminder] 分享即将到期: shareId={}, shareCode={}, expireTime={}",
            share.getId(),
            share.getShareCode(),
            share.getExpireTime());
      }
    } catch (Exception e) {
      log.error("[ShareExpiryReminder] 扫描到期分享失败", e);
    }
  }

  /**
   * 向分享创建者发送到期提醒站内信。
   *
   * <p>通知发送异常不影响主流程（NotifyHelper 内部已做异常隔离）。
   *
   * @param share 即将到期的分享链接 DTO
   */
  private void notifyExpiryReminder(ShareLinkDTO share) {
    if (share.getCreatedBy() == null || share.getCreatedBy().isBlank()) {
      return;
    }
    String title = share.getTitle() != null && !share.getTitle().isBlank()
        ? share.getTitle() : "分享链接";
    String expireTimeStr = share.getExpireTime() != null
        ? share.getExpireTime().toString() : "即将";
    notifyHelper.sendInApp(share.getCreatedBy(),
        "分享链接即将到期",
        String.format("您创建的分享「%s」将在 %s 到期，请及时续期或处理。", title, expireTimeStr));
  }
}
