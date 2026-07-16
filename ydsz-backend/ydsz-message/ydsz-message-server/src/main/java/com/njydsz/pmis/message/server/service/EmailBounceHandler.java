package com.njydsz.message.server.service.receipt;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.message.domain.entity.core.MsgLogDO;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 邮件退信处理器（P1-5）。
 *
 * <p>接收邮件服务商的退信回调，将对应消息日志标记为失败,
 * 并记录退信原因（硬退信/软退信），后续可用于清理无效邮箱。
 *
 * @author ydsz-team
 * @since 1.5.0
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
        msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLogDO>()
                .eq(MsgLogDO::getId, logId)
                .set(MsgLogDO::getStatus, "FAILED")
                .set(MsgLogDO::getReceiptStatus, "FAILED")
                .set(MsgLogDO::getReceiptAt, LocalDateTime.now())
                .set(MsgLogDO::getErrorMessage, fullReason));
        log.info("[EmailBounce] 退信处理: logId={} type={} recipient={} reason={}",
                logId, bounceType, recipient, reason);

        // 硬退信时记录无效邮箱（后续可用于用户通道绑定状态更新）
        if ("HARD".equalsIgnoreCase(bounceType)) {
            log.warn("[EmailBounce] 硬退信,建议标记邮箱无效: recipient={}", recipient);
        }
    }
}
