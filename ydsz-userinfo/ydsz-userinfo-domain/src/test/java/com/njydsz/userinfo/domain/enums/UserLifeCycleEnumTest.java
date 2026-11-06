package com.njydsz.userinfo.domain.enums;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserLifeCycleEnum} 单元测试。
 *
 * <p>覆盖：解析（大小写/空值/非法/遗留 0/1）、终态判定、状态流转规则、登录权限。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("用户生命周期枚举")
class UserLifeCycleEnumTest {

  @Nested
  @DisplayName("parse 解析")
  class Parse {

    @ParameterizedTest
    @CsvSource({
        "PENDING, PENDING",
        "ENABLED, ENABLED",
        "SUSPENDED, SUSPENDED",
        "DISABLED, DISABLED",
        "RESIGNED, RESIGNED",
        "pending, PENDING",
        "enabled, ENABLED",
        "Enabled, ENABLED",
        "RESIGNED, RESIGNED",
        "0, DISABLED",
        "1, ENABLED"
    })
    @DisplayName("合法值解析正确（含大小写与遗留 0/1）")
    void validValues(String input, UserLifeCycleEnum expected) {
      assertThat(UserLifeCycleEnum.parse(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "INVALID", "2", "enabled "})
    @DisplayName("空值/空白/非法值返回 null")
    void invalidValuesReturnNull(String input) {
      assertThat(UserLifeCycleEnum.parse(input)).isNull();
    }
  }

  @Nested
  @DisplayName("isTerminal 终态判定")
  class Terminal {

    @Test
    @DisplayName("RESIGNED 是终态")
    void resignedIsTerminal() {
      assertThat(UserLifeCycleEnum.RESIGNED.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "PENDING",
        "ENABLED",
        "SUSPENDED",
        "DISABLED"
    })
    @DisplayName("非 RESIGNED 不是终态")
    void nonTerminal(UserLifeCycleEnum status) {
      assertThat(status.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("canTransitTo 状态流转")
  class Transition {

    @ParameterizedTest
    @CsvSource({
        "PENDING, ENABLED, true",
        "PENDING, SUSPENDED, false",
        "PENDING, DISABLED, false",
        "PENDING, RESIGNED, false",
        "ENABLED, SUSPENDED, true",
        "ENABLED, DISABLED, true",
        "ENABLED, RESIGNED, true",
        "ENABLED, PENDING, false",
        "SUSPENDED, ENABLED, true",
        "SUSPENDED, DISABLED, true",
        "SUSPENDED, RESIGNED, true",
        "SUSPENDED, PENDING, false",
        "DISABLED, ENABLED, true",
        "DISABLED, SUSPENDED, false",
        "DISABLED, RESIGNED, false",
        "RESIGNED, ENABLED, false",
        "RESIGNED, PENDING, false",
        "RESIGNED, RESIGNED, true",
        "ENABLED, ENABLED, true"
    })
    @DisplayName("流转规则正确")
    void transitionRules(UserLifeCycleEnum from, UserLifeCycleEnum to, boolean expected) {
      assertThat(from.canTransitTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("target 为 null 返回 false")
    void nullTargetReturnsFalse() {
      assertThat(UserLifeCycleEnum.ENABLED.canTransitTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("canLogin 登录权限")
  class Login {

    @Test
    @DisplayName("ENABLED 允许登录")
    void enabledCanLogin() {
      assertThat(UserLifeCycleEnum.ENABLED.canLogin()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "PENDING",
        "SUSPENDED",
        "DISABLED",
        "RESIGNED"
    })
    @DisplayName("非 ENABLED 不允许登录")
    void nonEnabledCannotLogin(UserLifeCycleEnum status) {
      assertThat(status.canLogin()).isFalse();
    }
  }

  @Nested
  @DisplayName("allStates 全量状态")
  class AllStates {

    @Test
    @DisplayName("返回全部 5 个枚举值")
    void returnsAll() {
      List<UserLifeCycleEnum> states = UserLifeCycleEnum.ENABLED.allStates();
      assertThat(states).hasSize(5);
      assertThat(states).containsExactly(UserLifeCycleEnum.values());
    }
  }

  @Nested
  @DisplayName("requireTransitTo 非法流转抛异常")
  class RequireTransit {

    @Test
    @DisplayName("非法流转抛 IllegalStateException")
    void illegalTransitionThrows() {
      assertThatThrownBy(() -> UserLifeCycleEnum.RESIGNED.requireTransitTo(UserLifeCycleEnum.ENABLED))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("合法流转不抛异常")
    void legalTransitionPasses() {
      UserLifeCycleEnum.ENABLED.requireTransitTo(UserLifeCycleEnum.SUSPENDED);
    }
  }
}
