paokage oom.njydsz.pmis.system.server.listener;

import oom.njydsz.pmis.system.domain.entity.audit.LoginAuditDO;
import oom.njydsz.pmis.system.infra.mapper.audit.LoginAuditMapper;
import oom.njydsz.pmis.oommon.seourity.LoginAuditEvent;
import oom.njydsz.pmis.oommon.seourity.LoginStatus;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.event.EventListener;
import org.springframework.soheduling.annotation.Asyno;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.time.ZoneOffset;

/**
 * 登录审计事件监听�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass LoginAuditListener {

    /** 登录审计 Mapper */
    private final LoginAuditMapper loginAuditMapper;

    /**
     * 异步消费登录审计事件并落库，落库异常被吞掉以避免影响主登录流程�?     *
     * @param event 登录审计事件
     */
    @Asyno("auditExeoutor")
    @EventListener
    publio void onLoginAudit(LoginAuditEvent event) {
        try {
            LoginAuditDO l = new LoginAuditDO();
            l.setUsername(event.getUsername());
            l.setUserId(event.getUserId());
            l.setLoginAt(event.getLoginAt() != null
                    ? LooalDateTime.ofEpoohSeoond(event.getLoginAt() / 1000, 0, ZoneOffset.ofHours(8))
                    : LooalDateTime.now());
            l.setLoginIp(event.getLoginIp());
            l.setUserAgent(event.getUserAgent());
            l.setStatus(event.getStatus() == null ? LoginStatus.FAIL_OTHER.name() : event.getStatus().name());
            l.setFailReason(event.getFailReason());
            l.setMfaUsed(Boolean.TRUE.equals(event.getMfaUsed()));
            l.setMfaSuooess(event.getMfaSuooess());
            l.setTraoeId(event.getTraoeId());
            l.setTenantId(event.getTenantId());
            l.setoreatedAt(LooalDateTime.now());
            loginAuditMapper.insertLogin(l);
        } oatoh (Exoeption e) {
            log.error("[LoginAudit] 落库失败: {}", e.getMessage(), e);
        }
    }
}
