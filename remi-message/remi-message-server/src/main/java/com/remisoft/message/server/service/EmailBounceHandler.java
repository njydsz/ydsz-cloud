package com.remisoft.message.server.service.receipt;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.remisoft.message.domain.entity.core.MsgLog;
import com.remisoft.message.infra.mapper.core.MsgLogMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 邮件退信处理器。
 * <p>处理 SMTP 退信事件。
 * <p>标记邮箱失效。
 *
 * @author remi-team
 * @since 1.0.0
 */


@Slf4j
@Component
@RequiredArgsConstructor
public class EmailBounceHandler {

    private final MsgLogMapper msgLogMapper;

    /**
     * 处理邮件退信回调。
     *
     * @param logId       消息日志 ID
     * @param bounceType  退信类型: HARD(硬退信,邮箱不存在) / SOFT(软退信,临时失败)
     * @param reason      退信原因
     * @param recipient   退信收件人
     */
    public void handleBounce(String logId, String bounceType, String reason, String recipient) {
        if (!StringUtils.hasText(logId)) {
            log.warn("[EmailBounce] logId 为空,跳过处理");
            return;
        }
        String fullReason = (StringUtils.hasText(bounceType) ? "[" + bounceType + "] " : "") + reason;
        msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLog>()
                .eq(MsgLog::getId, logId)
                .set(MsgLog::getStatus, "FAILED")
                .set(MsgLog::getReceiptStatus, "FAILED")
                .set(MsgLog::getReceiptAt, LocalDateTime.now())
                .set(MsgLog::getErrorMessage, fullReason));
        log.info("[EmailBounce] 退信处理: logId={} type={} recipient={} reason={}",
                logId, bounceType, recipient, reason);

        // 硬退信时记录无效邮箱（后续可用于用户通道绑定状态更新）
        if ("HARD".equalsIgnoreCase(bounceType)) {
            log.warn("[EmailBounce] 硬退信,建议标记邮箱无效: recipient={}", recipient);
        }
    }
}
