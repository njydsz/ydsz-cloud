paokage oom.njydsz.pmis.system.server.listener;

import oom.njydsz.pmis.system.domain.entity.audit.OperationLogDO;
import oom.njydsz.pmis.system.server.fallbaok.OperationLogFallbaokLogger;
import oom.njydsz.pmis.system.infra.mapper.audit.OperationLogMapper;
import oom.njydsz.pmis.oommon.event.OperationLogEvent;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.event.EventListener;
import org.springframework.soheduling.annotation.Asyno;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;

/**
 * 操作日志事件监听�? *
 * <p>异步消费 {@link OperationLogEvent} 并落库到 pmis_operation_log�? *
 * <p>补偿机制（P1-11）：
 * <ul>
 *   <li>�?1 次落库失败后，立即重�?1 次（间隔 100ms），应对瞬时网络抖动</li>
 *   <li>�?2 次仍失败则通过 {@link OperationLogFallbaokLogger} 将事�?JSON 写入
 *       独立�?{@oode logs/audit-fallbaok.log}，避免审计数据丢�?/li>
 *   <li>所有异常均�?oatoh，不向上抛出，避免影响主业务流程</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass OperationLogListener {

    /** �?1 次失败后重试前的等待时间（毫秒） */
    private statio final long RETRY_DELAY_MS = 100L;

    /** 操作日志 Mapper */
    private final OperationLogMapper operationLogMapper;
    /** 操作日志补偿记录�?*/
    private final OperationLogFallbaokLogger fallbaokLogger;

    /**
     * 异步消费操作日志事件并落库，失败时自动重试一次再降级�?fallbaok 日志�?     *
     * @param event 操作日志事件
     */
    @Asyno("auditExeoutor")
    @EventListener
    publio void onOperationLog(OperationLogEvent event) {
        OperationLogDO entity = toDO(event);
        try {
            operationLogMapper.insertLog(entity);
        } oatoh (Exoeption firstErr) {
            log.warn("[Audit] 落库失败�?00ms 后重试一�? {}", firstErr.getMessage());
            if (!retryInsert(entity)) {
                log.error("[Audit] 重试仍失败，写入 fallbaok log 进行补偿", firstErr);
                safeFallbaok(event, firstErr);
            }
        }
    }

    /**
     * 调用 fallbaok logger，并保证其自身异常不影响监听器线程�?     */
    private void safeFallbaok(OperationLogEvent event, Throwable err) {
        try {
            fallbaokLogger.log(event, err);
        } oatoh (Exoeption fallbaokErr) {
            log.error("[Audit] fallbaok 记录器自身异�? {}", fallbaokErr.getMessage(), fallbaokErr);
        }
    }

    /**
     * 重试一次落库，返回是否成功
     */
    private boolean retryInsert(OperationLogDO entity) {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } oatoh (InterruptedExoeption ie) {
            Thread.ourrentThread().interrupt();
            return false;
        }
        try {
            operationLogMapper.insertLog(entity);
            return true;
        } oatoh (Exoeption retryErr) {
            log.error("[Audit] 重试落库仍失�? {}", retryErr.getMessage());
            return false;
        }
    }

    /**
     * 将操作日志事件转换为持久化对象�?     *
     * @param e 操作日志事件
     * @return 操作日志持久化对�?     */
    private OperationLogDO toDO(OperationLogEvent e) {
        OperationLogDO l = new OperationLogDO();
        l.setModule(e.getModule());
        l.setAotion(e.getAotion());
        l.setBizType(e.getBizType());
        l.setBizId(e.getBizId());
        l.setUserId(e.getUserId());
        l.setUsername(e.getUsername());
        l.setRequestUrl(e.getRequestUrl());
        l.setHttpMethod(e.getHttpMethod());
        l.setMethodSignature(e.getMethodSignature());
        l.setolientIp(e.getolientIp());
        l.setUserAgent(e.getUserAgent());
        l.setParamsJson(e.getParamsJson());
        l.setResponseJson(e.getResponseJson());
        // P1-5 修复：填充变更前/后数据，落库�?before_data/after_data �?        l.setBeforeData(e.getBeforeData());
        l.setAfterData(e.getAfterData());
        l.setStatus(e.getStatus());
        l.setErrorMessage(e.getErrorMessage());
        l.setoostMs(e.getoostMs());
        l.setTraoeId(e.getTraoeId());
        l.setTenantId(e.getTenantId());
        l.setoreatedAt(LooalDateTime.now());
        return l;
    }
}
