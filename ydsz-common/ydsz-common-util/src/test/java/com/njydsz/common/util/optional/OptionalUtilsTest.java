package com.njydsz.common.util.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link OptionalUtils} 单元测试。
 *
 * <p>覆盖：Optional ↔ Stream 互转、链式 OR、集合过滤、缺省异常抛出。
 *
 * @since 26.09.19
 */
@DisplayName("OptionalUtils 测试")
class OptionalUtilsTest {

  @Nested
  @DisplayName("Optional ↔ Stream 互转")
  class StreamTest {

    @Test
    @DisplayName("stream: 有值时返回单元素流")
    void stream_present_returnsSingletonStream() {
      Stream<String> result = OptionalUtils.stream(Optional.of("hello"));
      assertThat(result).containsExactly("hello");
    }

    @Test
    @DisplayName("stream: 空 Optional 返回空流")
    void stream_empty_returnsEmptyStream() {
      Stream<String> result = OptionalUtils.stream(Optional.empty());
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("stream: null 抛出 NPE")
    void stream_null_throwsNpe() {
      assertThatThrownBy(() -> OptionalUtils.stream(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("链式 OR")
  class OrTest {

    @Test
    @DisplayName("or: primary 有值返回 primary")
    void or_primaryPresent_returnsPrimary() {
      Optional<String> result = OptionalUtils.or(
          Optional.of("primary"), () -> Optional.of("fallback"));
      assertThat(result).contains("primary");
    }

    @Test
    @DisplayName("or: primary 为空返回 fallback")
    void or_primaryEmpty_returnsFallback() {
      Optional<String> result = OptionalUtils.or(
          Optional.empty(), () -> Optional.of("fallback"));
      assertThat(result).contains("fallback");
    }

    @Test
    @DisplayName("or: null primary 抛出 NPE")
    void or_nullPrimary_throwsNpe() {
      assertThatThrownBy(() -> OptionalUtils.or(null, () -> Optional.of("fallback")))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("or: null supplier 抛出 NPE")
    void or_nullSupplier_throwsNpe() {
      assertThatThrownBy(() -> OptionalUtils.or(Optional.of("x"), null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("集合过滤（presence-only）")
  class PresentOnlyTest {

    @Test
    @DisplayName("presentOnly: 过滤空 Optional，保留有值")
    void presentOnly_filtersEmptyOptionals() {
      List<Optional<String>> optionals = List.of(
          Optional.of("a"), Optional.empty(), Optional.of("b"));
      List<String> result = OptionalUtils.presentOnly(optionals);
      assertThat(result).containsExactly("a", "b");
    }

    @Test
    @DisplayName("presentOnly: 全部为空返回空 List")
    void presentOnly_allEmpty_returnsEmpty() {
      List<Optional<String>> optionals = List.of(Optional.empty(), Optional.empty());
      List<String> result = OptionalUtils.presentOnly(optionals);
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("presentOnly: 集合含 null 元素视为 empty 跳过")
    void presentOnly_nullElementInCollection_skipped() {
      List<Optional<String>> optionals = new java.util.ArrayList<>();
      optionals.add(Optional.of("x"));
      optionals.add(null);
      optionals.add(Optional.of("y"));
      List<String> result = OptionalUtils.presentOnly(optionals);
      assertThat(result).containsExactly("x", "y");
    }

    @Test
    @DisplayName("presentOnly: null 集合抛出 NPE")
    void presentOnly_nullCollection_throwsNpe() {
      assertThatThrownBy(() -> OptionalUtils.presentOnly(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("缺省时抛出自定义异常")
  class OrElseThrowTest {

    @Test
    @DisplayName("orElseThrowOr: 有值返回值")
    void orElseThrowOr_present_returnsValue() {
      String result = OptionalUtils.orElseThrowOr(Optional.of("x"), () -> new RuntimeException("should not"));
      assertThat(result).isEqualTo("x");
    }

    @Test
    @DisplayName("orElseThrowOr: 空时抛出提供的异常")
    void orElseThrowOr_empty_throwsSuppliedException() {
      assertThatThrownBy(() -> OptionalUtils.orElseThrowOr(
          Optional.empty(), () -> new IllegalStateException("custom error")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("custom error");
    }

    @Test
    @DisplayName("orElseThrowOr: null optional 抛出 NPE")
    void orElseThrowOr_nullOptional_throwsNpe() {
      assertThatThrownBy(() -> OptionalUtils.orElseThrowOr(null, () -> new RuntimeException()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("orElseThrowOr: null supplier 抛出 NPE")
    void orElseThrowOr_nullSupplier_throwsNpe() {
      assertThatThrownBy(() -> OptionalUtils.orElseThrowOr(Optional.of("x"), null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
