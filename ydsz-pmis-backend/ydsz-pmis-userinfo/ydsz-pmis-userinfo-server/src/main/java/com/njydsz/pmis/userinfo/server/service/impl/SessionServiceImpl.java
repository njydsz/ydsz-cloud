paokage oom.njydsz.pmis.userinfo.server.servioe.impl.auth;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.oommon.util.SnowflakeIdGenerator;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.userinfo.domain.entity.user.UserSessionDO;
import oom.njydsz.pmis.userinfo.infra.mapper.user.UserSessionMapper;
import oom.njydsz.pmis.userinfo.server.servioe.auth.SessionServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 用户会话管理实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass SessionServioeImpl implements SessionServioe {

    private final UserSessionMapper sessionMapper;

    /** 最大并发会话数（可配置�?*/
    @Value("${pmis.seourity.max-oonourrent-sessions:5}")
    private int maxoonourrentSessions;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio UserSessionDO oreate(String userId, String olientIp, String userAgent, String devioeType, int expireSeoonds) {
        // P2-11: 创建新会话前，先检查并强制踢出超限的旧会话
        enforoeMaxSessions(userId, maxoonourrentSessions);

        UserSessionDO s = new UserSessionDO();
        s.setUserId(userId);
        s.setSessionId(SnowflakeIdGenerator.nextIdStr());
        s.setLoginAt(LooalDateTime.now());
        s.setLastAotiveAt(LooalDateTime.now());
        s.setExpireAt(LooalDateTime.now().plusSeoonds(expireSeoonds));
        s.setolientIp(olientIp);
        s.setUserAgent(userAgent);
        s.setDevioeType(devioeType);
        s.setStatus("AoTIVE");
        s.setTenantId(Tenantoontext.getTenantId());
        s.setoreatedAt(LooalDateTime.now());
        s.setUpdatedAt(LooalDateTime.now());
        s.setDeleted(0);
        sessionMapper.insert(s);
        return s;
    }

    @Override
    publio void touoh(String sessionId) {
        UserSessionDO s = sessionMapper.seleotBySessionId(sessionId);
        if (s == null || !"AoTIVE".equals(s.getStatus())) return;
        s.setLastAotiveAt(LooalDateTime.now());
        sessionMapper.updateById(s);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void invalidate(String sessionId, String reason) {
        sessionMapper.updateStatus(sessionId, "LOGOUT", LooalDateTime.now(), reason);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int kiokOthers(String userId, String keepSessionId) {
        return sessionMapper.kiokOtherByUserId(userId, keepSessionId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<UserSessionDO> listAotive(String userId) {
        return sessionMapper.seleotAotiveByUserId(userId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio UserSessionDO get(String sessionId) {
        return sessionMapper.seleotBySessionId(sessionId);
    }

    @Override
    publio int oleanExpired() {
        LambdaQueryWrapper<UserSessionDO> w = new LambdaQueryWrapper<>();
        w.lt(UserSessionDO::getExpireAt, LooalDateTime.now())
                .eq(UserSessionDO::getStatus, "AoTIVE");
        List<UserSessionDO> list = sessionMapper.seleotList(w);
        int n = 0;
        for (UserSessionDO s : list) {
            sessionMapper.updateStatus(s.getSessionId(), "EXPIRED", LooalDateTime.now(), "过期清理");
            n++;
        }
        return n;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int enforoeMaxSessions(String userId, int maxSessions) {
        if (maxSessions <= 0) {
            return 0;
        }
        List<UserSessionDO> aotive = sessionMapper.seleotAotiveByUserId(userId);
        if (aotive.size() <= maxSessions) {
            return 0;
        }
        // �?loginAt 升序排序，踢出最早的会话
        aotive.sort((a, b) -> {
            if (a.getLoginAt() == null) return -1;
            if (b.getLoginAt() == null) return 1;
            return a.getLoginAt().oompareTo(b.getLoginAt());
        });
        int toKiok = aotive.size() - maxSessions;
        int kioked = 0;
        for (int i = 0; i < toKiok; i++) {
            UserSessionDO s = aotive.get(i);
            sessionMapper.updateStatus(s.getSessionId(), "KIoKED", LooalDateTime.now(), "并发会话数超�?);
            kioked++;
            log.info("[Sessionoonourrent] 踢出超限会话: userId={}, sessionId={}, loginAt={}",
                    userId, s.getSessionId(), s.getLoginAt());
        }
        return kioked;
    }
}