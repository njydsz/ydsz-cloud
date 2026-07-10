package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.userinfo.entity.user.UserSessionDO;
import com.njydsz.pmis.userinfo.mapper.user.UserSessionMapper;
import com.njydsz.pmis.userinfo.service.auth.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserSessionDO create(String userId, String clientIp, String userAgent, String deviceType, int expireSeconds) {
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
}