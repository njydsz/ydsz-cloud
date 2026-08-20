package com.njydsz.message.server.service.receipt;

import java.time.LocalDateTime;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 邮件退信处理器。
 *
 * <p>处理 SMTP 退信事件。
 *
 * <p>标记邮箱失效。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailBounceHandler {

  private final MsgLogRepository msgLogRepository;

  /**
   * 处理邮件退信回调。
   *
   * @param logId 消息日志 ID
   * @param bounceType 退信类型: HARD(硬退信,邮箱不存在) / SOFT(软退信,临时失败)
   * @param reason 退信原因
   * @param recipient 退信收件人
   */
  public void handleBounce(String logId, String bounceType, String reason, String recipient) {
    if (!StringUtils.hasText(logId)) {
      log.warn("[EmailBounce] logId 为空,跳过处理");
      return;
    }
    String fullReason = (StringUtils.hasText(bounceType) ? "[" + bounceType + "] " : "") + reason;
    Optional<MsgLogVO> voOpt = msgLogRepository.findById(logId);
    if (voOpt.isPresent()) {
      MsgLogVO vo = voOpt.get();
      vo.setStatus("FAILED");
      vo.setReceiptStatus("FAILED");
      vo.setReceiptAt(LocalDateTime.now());
      vo.setErrorMessage(fullReason);
      msgLogRepository.update(vo);
    }
    log.info(
        "[EmailBounce] 退信处理: logId={} type={} recipient={} reason={}",
        logId,
        bounceType,
        recipient,
        reason);

    // 硬退信时记录无效邮箱（后续可用于用户通道绑定状态更新）
    if ("HARD".equalsIgnoreCase(bounceType)) {
      log.warn("[EmailBounce] 硬退信,建议标记邮箱无效: recipient={}", recipient);
    }
  }
}
