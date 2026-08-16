package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.impl.SenderQuotaService;

/**
 * Sender 配额管理 Handler。
 *
 * <p>校验发送方（bizType 或 system）是否还有可用配额，
 * 配额用尽时拒绝发送（区分于限流：配额是业务级日/月总量控制）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(900)
@RequiredArgsConstructor
public class QuotaHandler implements SendHandler {

    private final SenderQuotaService senderQuotaService;
    private final MessageMetrics messageMetrics;

    @Override
    public boolean handle(MessageRequest request, SendContext ctx) {
        String senderId = (ctx.getBizType() != null && !ctx.getBizType().isEmpty())
                ? ctx.getBizType() : SystemConstants.SYSTEM_USER_ID;
        if (!senderQuotaService.checkQuota(senderId, ctx.getChannel())) {
            messageMetrics.recordSend(ctx.getChannel(), "QUOTA_EXCEEDED", 0);
            throw SysException.builder()
                .resultCode(BaseResultCode.TOO_MANY_REQUESTS)
                .message("发送方配额已用尽: senderId=" + senderId)
                .build();
        }
        return true;
    }

    @Override
    public int order() {
        return 900;
    }
}
