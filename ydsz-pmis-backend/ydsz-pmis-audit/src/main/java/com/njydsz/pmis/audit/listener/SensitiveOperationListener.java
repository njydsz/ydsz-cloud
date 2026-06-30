package com.njydsz.pmis.audit.listener;

import com.njydsz.pmis.audit.entity.SensitiveOperationDO;
import com.njydsz.pmis.audit.mapper.SensitiveOperationMapper;
import com.njydsz.pmis.common.security.SensitiveOperationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 敏感操作审计监听器
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensitiveOperationListener {

    private final SensitiveOperationMapper mapper;

    @Async
    @EventListener
    public void onOp(SensitiveOperationEvent e) {
        try {
            SensitiveOperationDO d = new SensitiveOperationDO();
            d.setUserId(e.getUserId());
            d.setUsername(e.getUsername());
            d.setOperationCode(e.getOperationCode());
            d.setOperationName(e.getOperationName());
            d.setReAuthMethod(e.getReAuthMethod());
            d.setReAuthToken(e.getReAuthToken());
            d.setVerifiedAt(e.getVerifiedAt() != null
                    ? LocalDateTime.ofEpochSecond(e.getVerifiedAt() / 1000, 0, ZoneOffset.ofHours(8))
                    : LocalDateTime.now());
            d.setExpireAt(e.getExpireAt() != null
                    ? LocalDateTime.ofEpochSecond(e.getExpireAt() / 1000, 0, ZoneOffset.ofHours(8))
                    : LocalDateTime.now().plusMinutes(5));
            d.setClientIp(e.getClientIp());
            d.setTraceId(e.getTraceId());
            d.setTenantId(e.getTenantId());
            d.setCreatedAt(LocalDateTime.now());
            mapper.insertOp(d);
        } catch (Exception ex) {
            log.error("[SensitiveOp] 落库失败: {}", ex.getMessage(), ex);
        }
    }
}
