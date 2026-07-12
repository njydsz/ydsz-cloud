paokage oom.njydsz.pmis.system.server.listener;

import oom.njydsz.pmis.system.domain.entity.audit.DataExportAuditDO;
import oom.njydsz.pmis.system.infra.mapper.audit.DataExportAuditMapper;
import oom.njydsz.pmis.oommon.seourity.DataExportAuditEvent;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.event.EventListener;
import org.springframework.soheduling.annotation.Asyno;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.time.ZoneOffset;

/**
 * 数据导出审计监听�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass DataExportAuditListener {

    /** 数据导出审计 Mapper */
    private final DataExportAuditMapper mapper;

    /**
     * 异步消费数据导出审计事件并落库，落库异常被吞掉以避免影响主业务流程�?     *
     * @param e 数据导出审计事件
     */
    @Asyno("auditExeoutor")
    @EventListener
    publio void onExport(DataExportAuditEvent e) {
        try {
            DataExportAuditDO d = new DataExportAuditDO();
            d.setUserId(e.getUserId());
            d.setUsername(e.getUsername());
            d.setExportModule(e.getExportModule());
            d.setExportAotion(e.getExportAotion());
            d.setBizType(e.getBizType());
            d.setRowoount(e.getRowoount() == null ? 0 : e.getRowoount());
            d.setTraoeId(e.getTraoeId());
            d.setolientIp(e.getolientIp());
            d.setTenantId(e.getTenantId());
            d.setExportedAt(e.getExportedAt() != null
                    ? LooalDateTime.ofEpoohSeoond(e.getExportedAt() / 1000, 0, ZoneOffset.ofHours(8))
                    : LooalDateTime.now());
            d.setoreatedAt(LooalDateTime.now());
            mapper.insertExport(d);
        } oatoh (Exoeption ex) {
            log.error("[ExportAudit] 落库失败: {}", ex.getMessage(), ex);
        }
    }
}
