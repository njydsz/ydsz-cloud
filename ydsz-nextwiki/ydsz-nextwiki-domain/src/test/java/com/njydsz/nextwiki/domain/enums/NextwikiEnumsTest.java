package com.njydsz.nextwiki.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.njydsz.nextwiki.domain.enums.NextwikiEnums.ShareStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.TrashStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NextwikiEnums} 单元测试。
 *
 * <p>覆盖：ShareStatus / TrashStatus 的编码解析（大小写不敏感 / 空值 / 非法）、终态判定、状态流转规则。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("网盘知识库枚举集合")
class NextwikiEnumsTest {

  @Nested
  @DisplayName("ShareStatus 编码解析")
  class ShareStatusFromCode {

    @ParameterizedTest
    @CsvSource({"active,ACTIVE", "ACTIVE,ACTIVE", "Active,ACTIVE", "expired,EXPIRED", "revoked,REVOKED"})
    @DisplayName("大小写不敏感解析成功")
    void parsesCaseInsensitive(String input, ShareStatus expected) {
      assertThat(ShareStatus.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown", "deleted"})
    @DisplayName("空值或未匹配返回 null")
    void returnsNullForInvalid(String input) {
      assertThat(ShareStatus.fromCode(input)).isNull();
    }
  }

  @Nested
  @DisplayName("ShareStatus 终态判定")
  class ShareStatusTerminal {

    @ParameterizedTest
    @CsvSource({"ACTIVE,false", "EXPIRED,true", "REVOKED,true"})
    @DisplayName("isTerminal 与枚举语义一致")
    void terminalFlag(String name, boolean expected) {
      assertThat(ShareStatus.valueOf(name).isTerminal()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("ShareStatus 状态流转")
  class ShareStatusTransit {

    @ParameterizedTest
    @CsvSource({
      "ACTIVE,ACTIVE,true",
      "ACTIVE,EXPIRED,true",
      "ACTIVE,REVOKED,true",
      "EXPIRED,REVOKED,false",
      "REVOKED,ACTIVE,false"
    })
    @DisplayName("canTransitTo 覆盖全部分支")
    void canTransit(String from, String to, boolean expected) {
      assertThat(ShareStatus.valueOf(from).canTransitTo(ShareStatus.valueOf(to))).isEqualTo(expected);
    }

    @Test
    @DisplayName("目标为 null 返回 false")
    void nullTargetReturnsFalse() {
      assertThat(ShareStatus.ACTIVE.canTransitTo(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("TrashStatus 编码解析")
  class TrashStatusFromCode {

    @ParameterizedTest
    @CsvSource({"in_trash,IN_TRASH", "IN_TRASH,IN_TRASH", "restored,RESTORED", "purged,PURGED"})
    @DisplayName("大小写不敏感解析成功")
    void parsesCaseInsensitive(String input, TrashStatus expected) {
      assertThat(TrashStatus.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"trashed", "IN_TRASH "})
    @DisplayName("未匹配返回 null")
    void returnsNullForInvalid(String input) {
      assertThat(TrashStatus.fromCode(input)).isNull();
    }
  }

  @Nested
  @DisplayName("TrashStatus 终态判定与流转")
  class TrashStatusTerminalAndTransit {

    @ParameterizedTest
    @CsvSource({"IN_TRASH,false", "RESTORED,true", "PURGED,true"})
    @DisplayName("isTerminal 与枚举语义一致")
    void terminalFlag(String name, boolean expected) {
      assertThat(TrashStatus.valueOf(name).isTerminal()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
      "IN_TRASH,IN_TRASH,true",
      "IN_TRASH,RESTORED,true",
      "IN_TRASH,PURGED,true",
      "RESTORED,IN_TRASH,false",
      "PURGED,RESTORED,false"
    })
    @DisplayName("canTransitTo 覆盖全部分支")
    void canTransit(String from, String to, boolean expected) {
      assertThat(TrashStatus.valueOf(from).canTransitTo(TrashStatus.valueOf(to))).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("getCode 一致性")
  class CodeConsistency {

    @Test
    @DisplayName("ShareStatus code 与枚举语义一致")
    void shareStatusCode() {
      assertThat(ShareStatus.ACTIVE.getCode()).isEqualTo("active");
      assertThat(ShareStatus.EXPIRED.getCode()).isEqualTo("expired");
      assertThat(ShareStatus.REVOKED.getCode()).isEqualTo("revoked");
    }

    @Test
    @DisplayName("TrashStatus code 与枚举语义一致")
    void trashStatusCode() {
      assertThat(TrashStatus.IN_TRASH.getCode()).isEqualTo("in_trash");
      assertThat(TrashStatus.RESTORED.getCode()).isEqualTo("restored");
      assertThat(TrashStatus.PURGED.getCode()).isEqualTo("purged");
    }
  }
}
