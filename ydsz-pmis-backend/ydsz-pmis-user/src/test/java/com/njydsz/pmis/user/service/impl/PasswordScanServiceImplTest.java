package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.user.dto.PasswordScanResultDTO;
import com.njydsz.pmis.user.entity.UserAccountDO;
import com.njydsz.pmis.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PasswordScanServiceImpl 测试（P3-3）
 */
@DisplayName("PasswordScanServiceImpl 弱密码扫描")
class PasswordScanServiceImplTest {

    private UserAccountMapper userAccountMapper;
    private PasswordScanServiceImpl service;

    @BeforeEach
    void setUp() {
        userAccountMapper = mock(UserAccountMapper.class);
        service = new PasswordScanServiceImpl(userAccountMapper);
    }

    private UserAccountDO mkUser(long id, String name, LocalDateTime lastChange, Integer pwdCount) {
        UserAccountDO u = new UserAccountDO();
        u.setId(id);
        u.setUsername(name);
        u.setStatus("ENABLED");
        u.setLastPwdChangeAt(lastChange);
        u.setPwdChangeCount(pwdCount);
        return u;
    }

    @Test
    @DisplayName("scan 默认 90 天过期阈值")
    void scan_default() {
        LocalDateTime now = LocalDateTime.now();
        UserAccountDO healthy = mkUser(1L, "alice", now.minusDays(30), 3);
        UserAccountDO expiring = mkUser(2L, "bob", now.minusDays(70), 5);
        UserAccountDO expired = mkUser(3L, "carol", now.minusDays(120), 5);
        UserAccountDO initial = mkUser(4L, "dave", now.minusDays(5), 0);
        when(userAccountMapper.selectList(any())).thenReturn(List.of(healthy, expiring, expired, initial));

        PasswordScanResultDTO out = service.scan(0);
        assertThat(out.getTotalActive()).isEqualTo(4);
        assertThat(out.getExpiredCount()).isEqualTo(1);
        assertThat(out.getExpiringSoonCount()).isEqualTo(1);
        assertThat(out.getInitialPasswordCount()).isEqualTo(1);
        assertThat(out.getHealthyCount()).isEqualTo(1);
        assertThat(out.getExpiredAccounts().get(0).getRiskLevel()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("scan 空数据时全 0")
    void scan_empty() {
        when(userAccountMapper.selectList(any())).thenReturn(Collections.emptyList());
        PasswordScanResultDTO out = service.scan(90);
        assertThat(out.getTotalActive()).isEqualTo(0);
        assertThat(out.getHealthyCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("scan mapper 异常时降级为空结果")
    void scan_exception() {
        when(userAccountMapper.selectList(any())).thenThrow(new RuntimeException("DB down"));
        PasswordScanResultDTO out = service.scan(90);
        assertThat(out.getTotalActive()).isEqualTo(0);
    }

    @Test
    @DisplayName("scan 自定义阈值 60 天")
    void scan_customExpire() {
        LocalDateTime now = LocalDateTime.now();
        UserAccountDO u1 = mkUser(1L, "u1", now.minusDays(50), 1);
        UserAccountDO u2 = mkUser(2L, "u2", now.minusDays(80), 1);
        when(userAccountMapper.selectList(any())).thenReturn(List.of(u1, u2));

        PasswordScanResultDTO out = service.scan(60);
        // 50 天 < 60 - 30 = 30？不对，60-30=30，50>30 但 50<60 -> EXPIRING
        // 80 天 > 60 -> EXPIRED
        assertThat(out.getExpiredCount()).isEqualTo(1);
        assertThat(out.getExpiringSoonCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("scan lastPwdChangeAt 为 null 视为已过期")
    void scan_nullLastChange() {
        UserAccountDO u = mkUser(1L, "ghost", null, 1);
        when(userAccountMapper.selectList(any())).thenReturn(List.of(u));

        PasswordScanResultDTO out = service.scan(90);
        assertThat(out.getExpiredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("listInitialPasswordAccounts 包含 pwdCount=null/0")
    void listInitial() {
        when(userAccountMapper.selectList(any())).thenReturn(List.of(
                mkUser(1L, "u1", LocalDateTime.now(), 0),
                mkUser(2L, "u2", LocalDateTime.now(), null)));
        List<UserAccountDO> out = service.listInitialPasswordAccounts();
        assertThat(out).hasSize(2);
    }

    @Test
    @DisplayName("AccountRisk daysSinceChange 为负数表示已过期")
    void daysSinceChange_sign() {
        LocalDateTime now = LocalDateTime.now();
        UserAccountDO u = mkUser(1L, "u1", now.minusDays(100), 1);
        when(userAccountMapper.selectList(any())).thenReturn(List.of(u));
        PasswordScanResultDTO out = service.scan(90);
        assertThat(out.getExpiredAccounts().get(0).getDaysSinceChange()).isGreaterThanOrEqualTo(100);
    }
}
