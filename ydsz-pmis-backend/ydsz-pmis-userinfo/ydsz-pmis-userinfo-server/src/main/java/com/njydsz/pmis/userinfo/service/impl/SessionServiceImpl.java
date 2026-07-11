package com.njydsz.pmis.userinfo.server.service.impl.auth;

import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.userinfo.domain.entity.user.UserSessionDO;
import com.njydsz.pmis.userinfo.infra.mapper.user.UserSessionMapper;
import com.njydsz.pmis.userinfo.server.service.auth.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户会话管理实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final UserSessionMapper sessionMapper;

    /** 最大并发会话数（可配置） */
    @Value("${pmis.security.max-concurrent-sessions:5}")
    private int maxConcurrentSessions;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserSessionDO create(String userId, String clientIp, String userAgent, String deviceType, int expireSeconds) {
        // P2-11: 创建新会话前，先检查并强制踢出超限的旧会话
        enforceMaxSessions(userId, maxConcurrentSessions);

        UserSessionDO s = new UserSessionDO();
        s.setUserId(userId);
        s.setSessionId(SnowflakeIdGenerator.nextIdStr());
        s.setLoginAt(LocalDateTime.now());
        s.setLastActiveAt(LocalDateTime.now());
        s.setExpireAt(LocalDateTime.now().plusSeconds(expireSeconds));
        s.setClientIp(clientIp);
        s.setUserAgent(userAgent);
        s.setDeviceType(deviceType);
        s.setStatus("ACTIVE");
        s.setTenantId(TenantContext.getTenantId());
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        s.setDeleted(0);
        sessionMapper.insert(s);
        return s;
    }

    @Override
    public void touch(String sessionId) {
        UserSessionDO s = sessionMapper.selectBySessionId(sessionId);
        if (s == null || !"ACTIVE".equals(s.getStatus())) return;
        s.setLastActiveAt(LocalDateTime.now());
        sessionMapper.updateById(s);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void invalidate(String sessionId, String reason) {
        sessionMapper.updateStatus(sessionId, "LOGOUT", LocalDateTime.now(), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int kickOthers(String userId, String keepSessionId) {
        return sessionMapper.kickOtherByUserId(userId, keepSessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSessionDO> listActive(String userId) {
        return sessionMapper.selectActiveByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSessionDO get(String sessionId) {
        return sessionMapper.selectBySessionId(sessionId);
    }

    @Override
    public int cleanExpired() {
        LambdaQueryWrapper<UserSessionDO> w = new LambdaQueryWrapper<>();
        w.lt(UserSessionDO::getExpireAt, LocalDateTime.now())
                .eq(UserSessionDO::getStatus, "ACTIVE");
        List<UserSessionDO> list = sessionMapper.selectList(w);
        int n = 0;
        for (UserSessionDO s : list) {
            sessionMapper.updateStatus(s.getSessionId(), "EXPIRED", LocalDateTime.now(), "过期清理");
            n++;
        }
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int enforceMaxSessions(String userId, int maxSessions) {
        if (maxSessions <= 0) {
            return 0;
        }
        List<UserSessionDO> active = sessionMapper.selectActiveByUserId(userId);
        if (active.size() <= maxSessions) {
            return 0;
        }
        // 按 loginAt 升序排序，踢出最早的会话
        active.sort((a, b) -> {
            if (a.getLoginAt() == null) return -1;
            if (b.getLoginAt() == null) return 1;
            return a.getLoginAt().compareTo(b.getLoginAt());
        });
        int toKick = active.size() - maxSessions;
        int kicked = 0;
        for (int i = 0; i < toKick; i++) {
            UserSessionDO s = active.get(i);
            sessionMapper.updateStatus(s.getSessionId(), "KICKED", LocalDateTime.now(), "并发会话数超限");
            kicked++;
            log.info("[SessionConcurrent] 踢出超限会话: userId={}, sessionId={}, loginAt={}",
                    userId, s.getSessionId(), s.getLoginAt());
        }
        return kicked;
    }
}