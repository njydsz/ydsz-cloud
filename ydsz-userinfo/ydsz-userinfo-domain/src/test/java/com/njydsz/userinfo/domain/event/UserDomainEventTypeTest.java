package com.njydsz.userinfo.domain.event;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserDomainEventType} 单元测试。
 *
 * <p>覆盖：fromCode 解析（空值/非法/大小写敏感）、code 与 description 映射。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("用户领域事件类型枚举")
class UserDomainEventTypeTest {

  @Nested
  @DisplayName("fromCode 解析")
  class FromCode {

    @Test
    @DisplayName("USER_CREATED 解析正确")
    void userCreated() {
      assertThat(UserDomainEventType.fromCode("USER_CREATED"))
          .isEqualTo(UserDomainEventType.USER_CREATED);
    }

    @Test
    @DisplayName("USER_LOGIN 解析正确")
    void userLogin() {
      assertThat(UserDomainEventType.fromCode("USER_LOGIN"))
          .isEqualTo(UserDomainEventType.USER_LOGIN);
    }

    @Test
    @DisplayName("ROLE_CHANGED 解析正确")
    void roleChanged() {
      assertThat(UserDomainEventType.fromCode("ROLE_CHANGED"))
          .isEqualTo(UserDomainEventType.ROLE_CHANGED);
    }

    @Test
    @DisplayName("ORG_STRUCTURE_CHANGED 解析正确")
    void orgStructureChanged() {
      assertThat(UserDomainEventType.fromCode("ORG_STRUCTURE_CHANGED"))
          .isEqualTo(UserDomainEventType.ORG_STRUCTURE_CHANGED);
    }

    @Test
    @DisplayName("null 返回 null")
    void nullReturnsNull() {
      assertThat(UserDomainEventType.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("空白字符串返回 null")
    void blankReturnsNull() {
      assertThat(UserDomainEventType.fromCode("  ")).isNull();
    }

    @Test
    @DisplayName("未知 code 返回 null")
    void unknownReturnsNull() {
      assertThat(UserDomainEventType.fromCode("UNKNOWN")).isNull();
    }

    @Test
    @DisplayName("大小写敏感，小写不匹配")
    void caseSensitive() {
      assertThat(UserDomainEventType.fromCode("user_created")).isNull();
    }
  }

  @Nested
  @DisplayName("枚举属性")
  class Properties {

    @Test
    @DisplayName("每个枚举值的 code 与 description 非空")
    void allHaveCodeAndDescription() {
      for (UserDomainEventType type : UserDomainEventType.values()) {
        assertThat(type.getCode()).isNotBlank();
        assertThat(type.getDescription()).isNotBlank();
      }
    }

    @Test
    @DisplayName("description 是 i18n key 格式")
    void descriptionIsI18nKey() {
      assertThat(UserDomainEventType.USER_CREATED.getDescription()).startsWith("userinfo.event.");
    }
  }
}
