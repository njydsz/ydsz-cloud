package com.njydsz.pmis.audit.listener;

import com.njydsz.pmis.audit.entity.LoginAuditDO;
import com.njydsz.pmis.audit.mapper.LoginAuditMapper;
import com.njydsz.pmis.common.security.LoginAuditEvent;
import com.njydsz.pmis.common.security.LoginStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 登录审计事件监听器
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAuditListener {

    /** 登录审计 Mapper */
    private final LoginAuditMapper loginAuditMapper;

    /**
     * 异步消费登录审计事件并落库，落库异常被吞掉以避免影响主登录流程。
     *
     * @param event 登录审计事件
     */
    @Async
    @EventListener
    public void onLoginAudit(LoginAuditEvent event) {
        try {
            LoginAuditDO l = new LoginAuditDO();
            l.setUsername(event.getUsername());
            l.setUserId(event.getUserId());
            l.setLoginAt(event.getLoginAt() != null
                    ? LocalDateTime.ofEpochSecond(event.getLoginAt() / 1000, 0, java.time.ZoneOffset.ofHours(8))
                    : LocalDateTime.now());
            l.setLoginIp(event.getLoginIp());
            l.setUserAgent(event.getUserAgent());
            l.setStatus(event.getStatus() == null ? LoginStatus.FAIL_OTHER.name() : event.getStatus().name());
            l.setFailReason(event.getFailReason());
            l.setMfaUsed(Boolean.TRUE.equals(event.getMfaUsed()));
            l.setMfaSuccess(event.getMfaSuccess());
            l.setTraceId(event.getTraceId());
            l.setTenantId(event.getTenantId());
            l.setCreatedAt(LocalDateTime.now());
            loginAuditMapper.insertLogin(l);
        } catch (Exception e) {
            log.error("[LoginAudit] 落库失败: {}", e.getMessage(), e);
        }
    }
}
