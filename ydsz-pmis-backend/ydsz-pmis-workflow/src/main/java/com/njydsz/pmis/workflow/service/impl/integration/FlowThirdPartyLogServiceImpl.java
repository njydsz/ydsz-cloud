package com.njydsz.pmis.workflow.service.impl.integration;

import com.njydsz.pmis.workflow.entity.integration.FlowThirdPartyLogDO;
import com.njydsz.pmis.workflow.mapper.integration.FlowThirdPartyLogMapper;
import com.njydsz.pmis.workflow.service.integration.FlowThirdPartyLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 三方审批回调日志服务实现
 *
 * <p>核心能力：
 * <ul>
 *   <li>{@link #savePending} — 回调入口先落库（PENDING）</li>
 *   <li>{@link #updateSuccess} / {@link #updateFailed} — 处理完成后更新状态</li>
 * </ul>
 *
 * <p>容错策略：
 * <ul>
 *   <li>落库与状态更新均使用 REQUIRES_NEW 事务，避免回调主流程事务回滚导致日志丢失</li>
 *   <li>所有方法均 try-catch，保证日志异常不拖垮回调主流程</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowThirdPartyLogServiceImpl implements FlowThirdPartyLogService {

    /** 三方对接日志 Mapper，查询分页日志记录 */
    private final FlowThirdPartyLogMapper thirdPartyLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public String savePending(FlowThirdPartyLogDO logEntry) {
        try {
            if (logEntry == null) {
                return null;
            }
            logEntry.setHandleStatus(STATUS_PENDING);
            if (logEntry.getCreatedAt() == null) {
                logEntry.setCreatedAt(LocalDateTime.now());
            }
            thirdPartyLogMapper.insert(logEntry);
            return logEntry.getId();
        } catch (Exception e) {
            // 日志落库失败不阻塞回调主流程
            log.error("[ThirdPartyLog] 保存 PENDING 日志失败: platform={} eventType={} err={}",
                    logEntry != null ? logEntry.getPlatform() : null,
                    logEntry != null ? logEntry.getEventType() : null,
                    e.getMessage(), e);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void updateSuccess(String id) {
        updateStatus(id, STATUS_SUCCESS, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void updateFailed(String id, String errorMsg) {
        updateStatus(id, STATUS_FAIL, truncate(errorMsg, 512));
    }

    /**
     * 更新日志状态
     *
     * @param id       日志 ID
     * @param status   处理状态
     * @param errorMsg 失败原因
     */
    private void updateStatus(String id, String status, String errorMsg) {
        if (id == null) {
            return;
        }
        try {
            thirdPartyLogMapper.updateStatus(id, status, errorMsg);
        } catch (Exception e) {
            log.error("[ThirdPartyLog] 更新日志状态失败: id={} status={} err={}",
                    id, status, e.getMessage(), e);
        }
    }

    /**
     * 截断字符串到指定长度（避免超出数据库列长度限制）
     */
    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}
