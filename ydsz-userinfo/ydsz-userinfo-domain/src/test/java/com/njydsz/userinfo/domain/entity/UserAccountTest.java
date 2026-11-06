package com.njydsz.userinfo.domain.entity;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.userinfo.domain.enums.BanType;
import com.njydsz.userinfo.domain.enums.UserLifeCycleEnum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserAccount} 领域行为单元测试。
 *
 * <p>覆盖：封禁/解封（含临时封禁懒过期）、生命周期流转、登录失败锁定、登录成功重置。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("用户账号实体")
class UserAccountTest {

  private UserAccount account() {
    UserAccount a = new UserAccount();
    a.setLifeCycle(UserLifeCycleEnum.ENABLED);
    return a;
  }

  @Nested
  @DisplayName("封禁/解封")
  class Ban {

    @Test
    @DisplayName("永久封禁：isBanned 始终 true")
    void permanentBan() {
      UserAccount a = account();
      a.ban(BanType.PERMANENT, "违规", null, "admin");
      assertThat(a.isBanned()).isTrue();
      assertThat(a.getBanType()).isEqualTo("PERMANENT");
      assertThat(a.getBanReason()).isEqualTo("违规");
      assertThat(a.getBannedBy()).isEqualTo("admin");
      assertThat(a.getBannedAt()).isNotNull();
    }

    @Test
    @DisplayName("临时封禁未到期：isBanned true")
    void temporaryBanNotExpired() {
      UserAccount a = account();
      a.ban(BanType.TEMPORARY, "违规", LocalDateTime.now().plusHours(1), "admin");
      assertThat(a.isBanned()).isTrue();
    }

    @Test
    @DisplayName("临时封禁已到期：懒清除并返回 false")
    void temporaryBanExpiredAutoClears() {
      UserAccount a = account();
      a.ban(BanType.TEMPORARY, "违规", LocalDateTime.now().minusMinutes(1), "admin");
      assertThat(a.isBanned()).isFalse();
      assertThat(a.getBanType()).isNull();
      assertThat(a.getBanReason()).isNull();
      assertThat(a.getBanExpireAt()).isNull();
    }

    @Test
    @DisplayName("未封禁：isBanned false")
    void notBanned() {
      assertThat(account().isBanned()).isFalse();
    }

    @Test
    @DisplayName("解封清空封禁字段并记录操作人")
    void unbanClearsFields() {
      UserAccount a = account();
      a.ban(BanType.PERMANENT, "违规", null, "admin");
      a.unban("superadmin");
      assertThat(a.isBanned()).isFalse();
      assertThat(a.getBanType()).isNull();
      assertThat(a.getBannedBy()).isEqualTo("superadmin");
    }

    @Test
    @DisplayName("toBanInfo 反映封禁状态")
    void toBanInfoReflectsState() {
      UserAccount a = account();
      a.ban(BanType.PERMANENT, "违规", null, "admin");
      assertThat(a.toBanInfo().isBanned()).isTrue();
      assertThat(a.toBanInfo().getBanType()).isEqualTo("PERMANENT");
    }
  }

  @Nested
  @DisplayName("生命周期流转")
  class LifeCycle {

    @Test
    @DisplayName("PENDING → activate → ENABLED")
    void activateFromPending() {
      UserAccount a = new UserAccount();
      a.setLifeCycle(UserLifeCycleEnum.PENDING);
      a.activate();
      assertThat(a.getLifeCycle()).isEqualTo(UserLifeCycleEnum.ENABLED);
      assertThat(a.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("ENABLED → suspend → SUSPENDED")
    void suspend() {
      UserAccount a = account();
      a.suspend();
      assertThat(a.getLifeCycle()).isEqualTo(UserLifeCycleEnum.SUSPENDED);
    }

    @Test
    @DisplayName("SUSPENDED → resume → ENABLED 并清锁")
    void resumeClearsLock() {
      UserAccount a = account();
      a.suspend();
      a.setLockedUntil(LocalDateTime.now().plusMinutes(10));
      a.setLoginFailCount(3);
      a.resume();
      assertThat(a.getLifeCycle()).isEqualTo(UserLifeCycleEnum.ENABLED);
      assertThat(a.getLockedUntil()).isNull();
      assertThat(a.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("ENABLED → resign → RESIGNED（终态）")
    void resign() {
      UserAccount a = account();
      a.resign();
      assertThat(a.getLifeCycle()).isEqualTo(UserLifeCycleEnum.RESIGNED);
    }

    @Test
    @DisplayName("RESIGNED → 任意流转抛异常")
    void resignedIsTerminal() {
      UserAccount a = account();
      a.resign();
      assertThatThrownBy(a::suspend).isInstanceOf(RuntimeException.class);
      assertThatThrownBy(a::resume).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ENABLED → disable → DISABLED → activate → ENABLED")
    void disableThenReactivate() {
      UserAccount a = account();
      a.disable();
      assertThat(a.getLifeCycle()).isEqualTo(UserLifeCycleEnum.DISABLED);
      a.activate();
      assertThat(a.getLifeCycle()).isEqualTo(UserLifeCycleEnum.ENABLED);
    }
  }

  @Nested
  @DisplayName("登录锁定")
  class LoginLock {

    @Test
    @DisplayName("失败次数未达阈值不锁定")
    void notLockedBelowThreshold() {
      UserAccount a = account();
      a.recordLoginFailure(3, 30);
      assertThat(a.isLocked()).isFalse();
      assertThat(a.getLoginFailCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("失败次数达到阈值触发锁定")
    void lockedAtThreshold() {
      UserAccount a = account();
      a.recordLoginFailure(3, 30);
      a.recordLoginFailure(3, 30);
      a.recordLoginFailure(3, 30);
      assertThat(a.isLocked()).isTrue();
      assertThat(a.getLockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("unlock 清除锁定与失败计数")
    void unlockClears() {
      UserAccount a = account();
      a.recordLoginFailure(3, 30);
      a.recordLoginFailure(3, 30);
      a.recordLoginFailure(3, 30);
      a.unlock();
      assertThat(a.isLocked()).isFalse();
      assertThat(a.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("recordLoginSuccess 重置失败计数并记录登录信息")
    void loginSuccessResets() {
      UserAccount a = account();
      a.recordLoginFailure(3, 30);
      a.recordLoginSuccess("10.0.0.1");
      assertThat(a.getLoginFailCount()).isZero();
      assertThat(a.getLockedUntil()).isNull();
      assertThat(a.getLastLoginIp()).isEqualTo("10.0.0.1");
      assertThat(a.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("锁定期内 canLogin false")
    void cannotLoginWhenLocked() {
      UserAccount a = account();
      a.recordLoginFailure(2, 30);
      a.recordLoginFailure(2, 30);
      assertThat(a.canLogin()).isFalse();
    }

    @Test
    @DisplayName("ENABLED 且未锁定 canLogin true")
    void canLoginWhenEnabledAndUnlocked() {
      assertThat(account().canLogin()).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED canLogin false")
    void suspendedCannotLogin() {
      UserAccount a = account();
      a.suspend();
      assertThat(a.canLogin()).isFalse();
    }
  }
}
