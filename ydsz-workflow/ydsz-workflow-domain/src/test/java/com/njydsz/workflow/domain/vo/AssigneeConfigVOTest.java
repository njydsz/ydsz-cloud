package com.njydsz.workflow.domain.vo;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.vo.AssigneeConfigVO.EmptyStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssigneeConfigVO} 单元测试。
 *
 * <p>覆盖：fromExt 解析（空 Map/默认值）、resolveFallbackUserId 策略映射、requiresFallback、fromExtJson 容错。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("办理人配置值对象")
class AssigneeConfigVOTest {

  @Nested
  @DisplayName("fromExt 解析")
  class FromExt {

    @Test
    @DisplayName("null Map 使用默认值")
    void nullMapUsesDefaults() {
      AssigneeConfigVO config = AssigneeConfigVO.fromExt(null);
      assertThat(config.getEmptyStrategy()).isEqualTo(EmptyStrategy.AUTO_PASS);
      assertThat(config.getAdminUserId()).isEqualTo("1");
      assertThat(config.getSpecifiedUserId()).isEqualTo("1");
    }

    @Test
    @DisplayName("空 Map 使用默认值")
    void emptyMapUsesDefaults() {
      AssigneeConfigVO config = AssigneeConfigVO.fromExt(Map.of());
      assertThat(config.getEmptyStrategy()).isEqualTo(EmptyStrategy.AUTO_PASS);
    }

    @Test
    @DisplayName("解析 TRANSFER_ADMIN 策略与 adminUserId")
    void transferAdmin() {
      AssigneeConfigVO config =
          AssigneeConfigVO.fromExt(Map.of("emptyStrategy", "TRANSFER_ADMIN", "adminUserId", "42"));
      assertThat(config.getEmptyStrategy()).isEqualTo(EmptyStrategy.TRANSFER_ADMIN);
      assertThat(config.getAdminUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("解析 ASSIGN_SPECIFIED 策略与 specifiedUserId")
    void assignSpecified() {
      AssigneeConfigVO config =
          AssigneeConfigVO.fromExt(
              Map.of("emptyStrategy", "ASSIGN_SPECIFIED", "specifiedUserId", "99"));
      assertThat(config.getEmptyStrategy()).isEqualTo(EmptyStrategy.ASSIGN_SPECIFIED);
      assertThat(config.getSpecifiedUserId()).isEqualTo("99");
    }

    @Test
    @DisplayName("非法策略名回退 AUTO_PASS")
    void invalidStrategyFallsBack() {
      AssigneeConfigVO config = AssigneeConfigVO.fromExt(Map.of("emptyStrategy", "UNKNOWN"));
      assertThat(config.getEmptyStrategy()).isEqualTo(EmptyStrategy.AUTO_PASS);
    }

    @Test
    @DisplayName("策略名大小写不敏感")
    void caseInsensitive() {
      AssigneeConfigVO config = AssigneeConfigVO.fromExt(Map.of("emptyStrategy", "transfer_admin"));
      assertThat(config.getEmptyStrategy()).isEqualTo(EmptyStrategy.TRANSFER_ADMIN);
    }

    @Test
    @DisplayName("空白 userId 回退默认值")
    void blankUserIdFallsBack() {
      AssigneeConfigVO config =
          AssigneeConfigVO.fromExt(Map.of("adminUserId", "  ", "specifiedUserId", ""));
      assertThat(config.getAdminUserId()).isEqualTo("1");
      assertThat(config.getSpecifiedUserId()).isEqualTo("1");
    }
  }

  @Nested
  @DisplayName("fromExtJson 解析")
  class FromExtJson {

    @Test
    @DisplayName("null/空白字符串使用默认值")
    void nullOrBlankUsesDefaults() {
      assertThat(AssigneeConfigVO.fromExtJson(null).getEmptyStrategy())
          .isEqualTo(EmptyStrategy.AUTO_PASS);
      assertThat(AssigneeConfigVO.fromExtJson("  ").getEmptyStrategy())
          .isEqualTo(EmptyStrategy.AUTO_PASS);
    }

    @Test
    @DisplayName("合法 JSON 解析正确")
    void validJson() {
      AssigneeConfigVO config =
          AssigneeConfigVO.fromExtJson("{\"emptyStrategy\":\"TRANSFER_ADMIN\",\"adminUserId\":\"7\"}");
      assertThat(config.getEmptyStrategy()).isEqualTo(EmptyStrategy.TRANSFER_ADMIN);
      assertThat(config.getAdminUserId()).isEqualTo("7");
    }

    @Test
    @DisplayName("非法 JSON 容错回退默认值")
    void invalidJsonFallsBack() {
      AssigneeConfigVO config = AssigneeConfigVO.fromExtJson("not-json");
      assertThat(config.getEmptyStrategy()).isEqualTo(EmptyStrategy.AUTO_PASS);
      assertThat(config.getAdminUserId()).isEqualTo("1");
    }
  }

  @Nested
  @DisplayName("resolveFallbackUserId 策略映射")
  class ResolveFallback {

    @Test
    @DisplayName("AUTO_PASS 返回 null")
    void autoPassReturnsNull() {
      AssigneeConfigVO config = AssigneeConfigVO.fromExt(Map.of("emptyStrategy", "AUTO_PASS"));
      assertThat(config.resolveFallbackUserId()).isNull();
    }

    @Test
    @DisplayName("TRANSFER_ADMIN 返回 adminUserId")
    void transferAdminReturnsAdmin() {
      AssigneeConfigVO config =
          AssigneeConfigVO.fromExt(Map.of("emptyStrategy", "TRANSFER_ADMIN", "adminUserId", "42"));
      assertThat(config.resolveFallbackUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("ASSIGN_SPECIFIED 返回 specifiedUserId")
    void assignSpecifiedReturnsSpecified() {
      AssigneeConfigVO config =
          AssigneeConfigVO.fromExt(
              Map.of("emptyStrategy", "ASSIGN_SPECIFIED", "specifiedUserId", "99"));
      assertThat(config.resolveFallbackUserId()).isEqualTo("99");
    }
  }

  @Nested
  @DisplayName("requiresFallback 判定")
  class RequiresFallback {

    @Test
    @DisplayName("AUTO_PASS 不需要转交")
    void autoPassNoFallback() {
      assertThat(
              AssigneeConfigVO.fromExt(Map.of("emptyStrategy", "AUTO_PASS")).requiresFallback())
          .isFalse();
    }

    @Test
    @DisplayName("TRANSFER_ADMIN 需要转交")
    void transferAdminNeedsFallback() {
      assertThat(
              AssigneeConfigVO.fromExt(Map.of("emptyStrategy", "TRANSFER_ADMIN"))
                  .requiresFallback())
          .isTrue();
    }

    @Test
    @DisplayName("ASSIGN_SPECIFIED 需要转交")
    void assignSpecifiedNeedsFallback() {
      assertThat(
              AssigneeConfigVO.fromExt(Map.of("emptyStrategy", "ASSIGN_SPECIFIED"))
                  .requiresFallback())
          .isTrue();
    }
  }
}
