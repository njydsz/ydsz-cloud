package com.njydsz.pmis.audit.listener;

import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.fallback.OperationLogFallbackLogger;
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
 * <p>异步消费 {@link OperationLogEvent} 并落库到 pmis_operation_log。
 *
 * <p>补偿机制（P1-11）：
 * <ul>
 *   <li>第 1 次落库失败后，立即重试 1 次（间隔 100ms），应对瞬时网络抖动</li>
 *   <li>第 2 次仍失败则通过 {@link OperationLogFallbackLogger} 将事件 JSON 写入
 *       独立的 {@code logs/audit-fallback.log}，避免审计数据丢失</li>
 *   <li>所有异常均被 catch，不向上抛出，避免影响主业务流程</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogListener {

    /** 第 1 次失败后重试前的等待时间（毫秒） */
    private static final long RETRY_DELAY_MS = 100L;

    /** 操作日志 Mapper */
    private final OperationLogMapper operationLogMapper;
    /** 操作日志补偿记录器 */
    private final OperationLogFallbackLogger fallbackLogger;

    /**
     * 异步消费操作日志事件并落库，失败时自动重试一次再降级到 fallback 日志。
     *
     * @param event 操作日志事件
     */
    @Async
    @EventListener
    public void onOperationLog(OperationLogEvent event) {
        OperationLogDO entity = toDO(event);
        try {
            operationLogMapper.insertLog(entity);
        } catch (Exception firstErr) {
            log.warn("[Audit] 落库失败，100ms 后重试一次: {}", firstErr.getMessage());
            if (!retryInsert(entity)) {
                log.error("[Audit] 重试仍失败，写入 fallback log 进行补偿", firstErr);
                safeFallback(event, firstErr);
            }
        }
    }

    /**
     * 调用 fallback logger，并保证其自身异常不影响监听器线程。
     */
    private void safeFallback(OperationLogEvent event, Throwable err) {
        try {
            fallbackLogger.log(event, err);
        } catch (Exception fallbackErr) {
            log.error("[Audit] fallback 记录器自身异常: {}", fallbackErr.getMessage(), fallbackErr);
        }
    }

    /**
     * 重试一次落库，返回是否成功
     */
    private boolean retryInsert(OperationLogDO entity) {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            operationLogMapper.insertLog(entity);
            return true;
        } catch (Exception retryErr) {
            log.error("[Audit] 重试落库仍失败: {}", retryErr.getMessage());
            return false;
        }
    }

    /**
     * 将操作日志事件转换为持久化对象。
     *
     * @param e 操作日志事件
     * @return 操作日志持久化对象
     */
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
