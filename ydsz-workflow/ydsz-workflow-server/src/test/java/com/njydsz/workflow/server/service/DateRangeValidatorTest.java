package com.njydsz.workflow.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.exception.WorkflowException;

/**
 * {@link DateRangeValidator} 单元测试。
 *
 * <p>覆盖时间范围校验的核心场景：合法范围返回规范化数组，起始晚于结束抛异常，
 * 起止相同抛异常（range=0），单侧 null 使用默认值，超过最大范围抛异常。
 *
 * <p>校验器为纯静态工具类，无需 Mock 依赖；测试通过输入/输出行为验证。
 *
 * @author ydsz-smoke-test
 * @since 26.10.12
 */
class DateRangeValidatorTest {

  // ============================== 合法范围 ==============================

  @Nested
  @DisplayName("validateAndNormalize — 合法范围（validRange）")
  class ValidRange {

    @Test
    @DisplayName("合法范围：起始早于结束，返回规范化数组")
    void validRange_startBeforeEnd_returnsNormalizedArray() {
      LocalDate[] result = DateRangeValidator.validateAndNormalize("2024-01-01", "2024-01-31");

      assertThat(result).hasSize(2);
      assertThat(result[0]).isEqualTo(LocalDate.of(2024, 1, 1));
      assertThat(result[1]).isEqualTo(LocalDate.of(2024, 1, 31));
    }

    @Test
    @DisplayName("合法范围：结束日期为今天（不含未来）")
    void validRange_endDateIsToday_returnsNormalizedArray() {
      LocalDate today = LocalDate.now();
      LocalDate monthAgo = today.minusDays(30);

      LocalDate[] result =
          DateRangeValidator.validateAndNormalize(monthAgo.toString(), today.toString());

      assertThat(result[1]).isEqualTo(today);
    }
  }

  // ============================== 反向范围 ==============================

  @Nested
  @DisplayName("validateAndNormalize — 反向范围（reverseRange）")
  class ReverseRange {

    @Test
    @DisplayName("反向范围：起始晚于结束应抛出 WorkflowException")
    void reverseRange_startAfterEnd_throwsException() {
      assertThatThrownBy(
              () -> DateRangeValidator.validateAndNormalize("2024-02-01", "2024-01-01"))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== 起止相同 ==============================

  @Nested
  @DisplayName("validateAndNormalize — 起止相同（equalStartEnd）")
  class EqualStartEnd {

    @Test
    @DisplayName("起止相同：range=0 属于合法范围（单日查询）")
    void equalStartEnd_sameDate_returnsSingleDayRange() {
      LocalDate[] result = DateRangeValidator.validateAndNormalize("2024-06-15", "2024-06-15");

      assertThat(result[0]).isEqualTo(LocalDate.of(2024, 6, 15));
      assertThat(result[1]).isEqualTo(LocalDate.of(2024, 6, 15));
    }
  }

  // ============================== 空起始/结束 ==============================

  @Nested
  @DisplayName("validateAndNormalize — 空值处理（nullStart/nullEnd）")
  class NullStart {

    @Test
    @DisplayName("双侧 null：默认返回近 30 天到今天")
    void bothNull_returnsDefaultRange() {
      LocalDate today = LocalDate.now();
      LocalDate[] result = DateRangeValidator.validateAndNormalize(null, null);

      assertThat(result[0]).isEqualTo(today.minusDays(DateRangeValidator.DEFAULT_RANGE_DAYS));
      assertThat(result[1]).isEqualTo(today);
    }

    @Test
    @DisplayName("起始 null + 结束非 null：起始回退为结束前 30 天")
    void nullStart_returnsEndDateMinusDefaultRange() {
      LocalDate endDate = LocalDate.of(2024, 6, 30);
      LocalDate[] result = DateRangeValidator.validateAndNormalize(null, endDate.toString());

      assertThat(result[0]).isEqualTo(endDate.minusDays(DateRangeValidator.DEFAULT_RANGE_DAYS));
      assertThat(result[1]).isEqualTo(endDate);
    }

    @Test
    @DisplayName("起始非 null + 结束 null：结束推进为起始后 30 天（不超过今天）")
    void nullEnd_returnsStartDatePlusDefaultRange() {
      // 使用过去的日期，保证起始+30 天不超出今天
      LocalDate startDate = LocalDate.now().minusDays(60);
      LocalDate[] result = DateRangeValidator.validateAndNormalize(startDate.toString(), null);

      assertThat(result[0]).isEqualTo(startDate);
      // 结束 = min(起始+30, 今天)
      LocalDate expectedEnd = startDate.plusDays(DateRangeValidator.DEFAULT_RANGE_DAYS);
      LocalDate today = LocalDate.now();
      assertThat(result[1]).isEqualTo(expectedEnd.isAfter(today) ? today : expectedEnd);
    }
  }

  // ============================== 超出最大范围 ==============================

  @Nested
  @DisplayName("validateAndNormalize — 超出最大范围（exceedMaxRange）")
  class ExceedMaxRange {

    @Test
    @DisplayName("超出最大范围：start～end 超过 365 天应抛出 WorkflowException")
    void exceedMaxRange_moreThan365Days_throwsException() {
      LocalDate start = LocalDate.of(2023, 1, 1);
      LocalDate end = LocalDate.of(2024, 1, 2); // 差 366 天

      assertThatThrownBy(
              () -> DateRangeValidator.validateAndNormalize(start.toString(), end.toString()))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("边界：恰好 365 天属于合法范围")
    void exceedMaxRange_exactly365Days_passes() {
      LocalDate end = LocalDate.now();
      LocalDate start = end.minusDays(DateRangeValidator.MAX_RANGE_DAYS);

      LocalDate[] result = DateRangeValidator.validateAndNormalize(start.toString(), end.toString());

      long daysBetween = ChronoUnit.DAYS.between(result[0], result[1]);
      assertThat(daysBetween).isLessThanOrEqualTo(DateRangeValidator.MAX_RANGE_DAYS);
    }
  }
}
