package com.njydsz.pmis.userinfo.server.service.impl.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.userinfo.domain.dto.auth.PasswordScanResultDTO;
import com.njydsz.pmis.userinfo.domain.entity.user.UserAccountDO;
import com.njydsz.pmis.userinfo.infra.mapper.user.UserAccountMapper;
import com.njydsz.pmis.userinfo.server.service.auth.PasswordScanService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 弱密码/过期密码扫描服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordScanServiceImpl implements PasswordScanService {

    private final UserAccountMapper userAccountMapper;

    /** 即将过期阈值（30 天） */
    private static final int EXPIRING_SOON_DAYS = 30;

    @Override
    @Transactional(readOnly = true)
    public PasswordScanResultDTO scan(int expireDays) {
        int realExpireDays = expireDays <= 0 ? 90 : expireDays;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireThreshold = now.minusDays(realExpireDays);
        LocalDateTime expiringSoonThreshold = now.minusDays(realExpireDays - EXPIRING_SOON_DAYS);

        // 1) 启用账号总数
        LambdaQueryWrapper<UserAccountDO> activeW = new LambdaQueryWrapper<>();
        activeW.eq(UserAccountDO::getStatus, "ENABLED");
        List<UserAccountDO> allActive = safeList(activeW);

        // 2) 过期账号
        List<UserAccountDO> expired = new ArrayList<>();
        // 3) 即将过期
        List<UserAccountDO> expiringSoon = new ArrayList<>();
        // 4) 初始密码
        List<UserAccountDO> initialPwd = new ArrayList<>();

        for (UserAccountDO u : allActive) {
            if (u == null || u.getId() == null) continue;

            // 初始密码：pwdChangeCount 为 null/0 且从未设置过
            if (u.getPwdChangeCount() == null || u.getPwdChangeCount() == 0) {
                initialPwd.add(u);
            }

            LocalDateTime lastChange = u.getLastPwdChangeAt();
            if (lastChange == null) {
                // 从未设置过时间 = 视为已过期
                expired.add(u);
            } else if (lastChange.isBefore(expireThreshold)) {
                expired.add(u);
            } else if (lastChange.isBefore(expiringSoonThreshold)) {
                expiringSoon.add(u);
            }
        }

        // 5) 组装结果
        PasswordScanResultDTO out = new PasswordScanResultDTO();
        out.setScannedAt(now);
        out.setExpireDays(realExpireDays);
        out.setTotalActive(allActive.size());
        out.setExpiredCount(expired.size());
        out.setExpiringSoonCount(expiringSoon.size());
        out.setInitialPasswordCount(initialPwd.size());
        out.setHealthyCount(allActive.size() - expired.size() - expiringSoon.size() - initialPwd.size());
        out.setExpiredAccounts(toRisks(expired, "EXPIRED", "强制改密", now));
        out.setExpiringSoonAccounts(toRisks(expiringSoon, "EXPIRING_SOON", "提醒改密", now));
        out.setInitialPasswordAccounts(toRisks(initialPwd, "INITIAL_PASSWORD", "首次登录强制改密", now));

        log.info("[PasswordScan] 总启用={} 健康={} 过期={} 即将过期={} 初始密码={}",
                allActive.size(), out.getHealthyCount(), expired.size(), expiringSoon.size(), initialPwd.size());
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccountDO> listExpiredAccounts(int expireDays) {
        int realExpireDays = expireDays <= 0 ? 90 : expireDays;
        LocalDateTime threshold = LocalDateTime.now().minusDays(realExpireDays);
        LambdaQueryWrapper<UserAccountDO> w = new LambdaQueryWrapper<>();
        w.eq(UserAccountDO::getStatus, "ENABLED")
                .and(qw -> qw.isNull(UserAccountDO::getLastPwdChangeAt)
                        .or().lt(UserAccountDO::getLastPwdChangeAt, threshold));
        return safeList(w);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccountDO> listExpiringSoonAccounts(int expireDays) {
        int realExpireDays = expireDays <= 0 ? 90 : expireDays;
        LocalDateTime expireThreshold = LocalDateTime.now().minusDays(realExpireDays);
        LocalDateTime expiringSoonThreshold = LocalDateTime.now().minusDays(realExpireDays - EXPIRING_SOON_DAYS);
        LambdaQueryWrapper<UserAccountDO> w = new LambdaQueryWrapper<>();
        w.eq(UserAccountDO::getStatus, "ENABLED")
                .ge(UserAccountDO::getLastPwdChangeAt, expiringSoonThreshold)
                .lt(UserAccountDO::getLastPwdChangeAt, expireThreshold);
        return safeList(w);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccountDO> listInitialPasswordAccounts() {
        LambdaQueryWrapper<UserAccountDO> w = new LambdaQueryWrapper<>();
        w.eq(UserAccountDO::getStatus, "ENABLED")
                .and(qw -> qw.isNull(UserAccountDO::getPwdChangeCount)
                        .or().eq(UserAccountDO::getPwdChangeCount, 0));
        return safeList(w);
    }

    // ----------------- 私有 -----------------

    private List<UserAccountDO> safeList(LambdaQueryWrapper<UserAccountDO> wrapper) {
        try {
            return userAccountMapper.selectList(wrapper);
        } catch (Exception e) {
            log.warn("[PasswordScan] 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<PasswordScanResultDTO.AccountRisk> toRisks(List<UserAccountDO> users,
                                                             String level,
                                                             String action,
                                                             LocalDateTime now) {
        if (CollectionUtils.isEmpty(users)) {
            return new ArrayList<>();
        }
        List<PasswordScanResultDTO.AccountRisk> out = new ArrayList<>(users.size());
        for (UserAccountDO u : users) {
            PasswordScanResultDTO.AccountRisk r = new PasswordScanResultDTO.AccountRisk();
            r.setUserId(u.getId());
            r.setUsername(u.getUsername());
            r.setLastPwdChangeAt(u.getLastPwdChangeAt());
            r.setRiskLevel(level);
            r.setAction(action);
            // 计算 daysSinceChange（负数=已过期 N 天）
            LocalDate baseDate = u.getLastPwdChangeAt() != null
                    ? u.getLastPwdChangeAt().toLocalDate()
                    : LocalDate.of(2000, 1, 1);
            r.setDaysSinceChange((int) ChronoUnit.DAYS.between(baseDate, now.toLocalDate()));
            out.add(r);
        }
        return out;
    }
}