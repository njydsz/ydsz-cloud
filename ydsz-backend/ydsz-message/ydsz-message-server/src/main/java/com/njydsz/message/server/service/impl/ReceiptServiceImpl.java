package com.njydsz.message.server.service.impl.receipt;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.message.domain.dto.receipt.ReceiptCallbackDTO;
import com.njydsz.message.domain.entity.receipt.MsgReceipt;
import com.njydsz.message.infra.mapper.receipt.MsgReceiptMapper;
import com.njydsz.message.server.service.core.MessageLogService;
import com.njydsz.message.server.service.receipt.ReceiptService;
import com.njydsz.message.server.tracing.MessageTraceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息回执服务实现。
 *
 * <p>回调落库 {@code MsgReceipt}，并联动 {@link MessageLogService#updateReceipt} 更新日志回执状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    /** 消息回执 Mapper */
    private final MsgReceiptMapper msgReceiptMapper;
    /** 消息日志服务（联动更新回执状态） */
    private final MessageLogService messageLogService;

    @Override
    public void callback(ReceiptCallbackDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getLogId())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "回执关联日志 ID 不能为空");
        }
        // P1-3: 回执回调进入追踪上下文（外部回调无原始 traceId，自动生成）
        try (MessageTraceContext ctx = MessageTraceContext.enter(null)) {
            MsgReceipt entity = new MsgReceipt();
            entity.setLogId(dto.getLogId());
            entity.setProviderTraceId(dto.getProviderTraceId());
            entity.setReceiptType(dto.getReceiptType());
            entity.setReceiptTime(LocalDateTime.now());
            entity.setProviderCode(dto.getProviderCode());
            entity.setProviderMsg(dto.getProviderMsg());
            entity.setRawResponse(dto.getRawResponse());
            entity.setTenantId(TenantContext.getTenantId());
            msgReceiptMapper.insert(entity);
            // 联动更新日志回执状态
            try {
                messageLogService.updateReceipt(dto.getLogId(), dto.getReceiptType(), entity.getReceiptTime());
            } catch (Exception e) {
                // 日志不存在时仅记录，不影响回执落库
                log.warn("[Receipt] 更新日志回执失败: logId={} err={}", dto.getLogId(), e.getMessage(), e);
            }
            log.info("[Receipt] 回执落库: logId={} type={}", dto.getLogId(), dto.getReceiptType());
        }
    }

    @Override
    public List<MsgReceipt> listByLogId(String logId) {
        if (!StringUtils.hasText(logId)) {
            return List.of();
        }
        return msgReceiptMapper.selectList(new LambdaQueryWrapper<MsgReceipt>()
                .eq(MsgReceipt::getLogId, logId)
                .orderByDesc(MsgReceipt::getReceiptTime));
    }
}
