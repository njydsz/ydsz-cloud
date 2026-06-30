package com.njydsz.pmis.audit.listener;

import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.mapper.OperationLogMapper;
import com.njydsz.pmis.common.event.OperationLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作日志事件监听器
 *
 * <p>异步消费 OperationLogEvent 并落库到 pmis_operation_log。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogListener {

    private final OperationLogMapper operationLogMapper;

    @Async
    @EventListener
    public void onOperationLog(OperationLogEvent event) {
        try {
            OperationLogDO l = toDO(event);
            operationLogMapper.insertLog(l);
        } catch (Exception e) {
            log.error("[Audit] 落库失败: {}", e.getMessage(), e);
        }
    }

    private OperationLogDO toDO(OperationLogEvent e) {
        OperationLogDO l = new OperationLogDO();
        l.setModule(e.getModule());
        l.setAction(e.getAction());
        l.setBizType(e.getBizType());
        l.setBizId(e.getBizId());
        l.setUserId(e.getUserId());
        l.setUsername(e.getUsername());
        l.setRequestUrl(e.getRequestUrl());
        l.setHttpMethod(e.getHttpMethod());
        l.setMethodSignature(e.getMethodSignature());
        l.setClientIp(e.getClientIp());
        l.setUserAgent(e.getUserAgent());
        l.setParamsJson(e.getParamsJson());
        l.setResponseJson(e.getResponseJson());
        l.setStatus(e.getStatus());
        l.setErrorMessage(e.getErrorMessage());
        l.setCostMs(e.getCostMs());
        l.setTraceId(e.getTraceId());
        l.setTenantId(e.getTenantId());
        l.setCreatedAt(LocalDateTime.now());
        return l;
    }
}
