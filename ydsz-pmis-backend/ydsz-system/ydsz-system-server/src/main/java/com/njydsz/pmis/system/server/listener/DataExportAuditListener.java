package com.njydsz.system.server.listener;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.audit.event.DataExportAuditEvent;
import com.njydsz.system.domain.entity.audit.DataExportAuditDO;
import com.njydsz.system.infra.mapper.audit.DataExportAuditMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据导出审计监听器
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataExportAuditListener {

    /** 数据导出审计 Mapper */
    private final DataExportAuditMapper mapper;

    /**
     * 异步消费数据导出审计事件并落库，落库异常被吞掉以避免影响主业务流程。
     *
     * @param e 数据导出审计事件
     */
    @Async("auditExecutor")
    @EventListener
    public void onExport(DataExportAuditEvent e) {
        try {
            DataExportAuditDO d = new DataExportAuditDO();
            d.setUserId(e.getUserId());
            d.setUsername(e.getUsername());
            d.setExportModule(e.getExportModule());
            d.setExportAction(e.getExportAction());
            d.setBizType(e.getBizType());
            d.setRowCount(e.getRowCount() == null ? 0 : e.getRowCount());
            d.setTraceId(e.getTraceId());
            d.setClientIp(e.getClientIp());
            d.setTenantId(e.getTenantId());
            d.setExportedAt(e.getExportedAt() != null
                    ? LocalDateTime.ofEpochSecond(e.getExportedAt() / 1000, 0, ZoneOffset.ofHours(8))
                    : LocalDateTime.now());
            d.setCreatedAt(LocalDateTime.now());
            mapper.insertExport(d);
        } catch (Exception ex) {
            log.error("[ExportAudit] 落库失败: {}", ex.getMessage(), ex);
        }
    }
}
