package com.njydsz.workflow.domain.vo;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.enums.FlowPerformType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CountersignConfigVO} 单元测试。
 *
 * <p>覆盖：fromExt 解析（默认值/approveCount/votePassRate）、isPassConditionMet 会签判定、isWeightPassConditionMet 票签判定。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("会签配置值对象")
class CountersignConfigVOTest {

  @Nested
  @DisplayName("fromExt 解析")
  class FromExt {

    @Test
    @DisplayName("null Map 使用默认值")
    void nullMapUsesDefaults() {
      CountersignConfigVO config = CountersignConfigVO.fromExt(null);
      assertThat(config.getPerformType()).isEqualTo(FlowPerformType.OR);
      assertThat(config.getApproveCount()).isEqualTo(1);
      assertThat(config.getVotePassRate()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("解析 PARALLEL 与 approveCount")
    void parallelWithCount() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(
              Map.of("performType", "PARALLEL", "approveCount", 3));
      assertThat(config.getPerformType()).isEqualTo(FlowPerformType.PARALLEL);
      assertThat(config.getApproveCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("解析 WEIGHTED 与 votePassRate")
    void weightedWithRate() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(
              Map.of("performType", "WEIGHTED", "votePassRate", 0.75));
      assertThat(config.getPerformType()).isEqualTo(FlowPerformType.WEIGHTED);
      assertThat(config.getVotePassRate()).isEqualByComparingTo("0.75");
    }

    @Test
    @DisplayName("approveCount ≤0 时强制为 1")
    void approveCountMinOne() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(Map.of("approveCount", 0));
      assertThat(config.getApproveCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("votePassRate 超出 [−1,1] 时截断")
    void voteRateClamped() {
      assertThat(
              CountersignConfigVO.fromExt(Map.of("votePassRate", 2.0)).getVotePassRate())
          .isEqualByComparingTo("1");
      assertThat(
              CountersignConfigVO.fromExt(Map.of("votePassRate", -2.0)).getVotePassRate())
          .isEqualByComparingTo("-1");
    }

    @Test
    @DisplayName("非法 performType 回退 OR")
    void invalidTypeFallsBack() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(Map.of("performType", "BOGUS"));
      assertThat(config.getPerformType()).isEqualTo(FlowPerformType.OR);
    }
  }

  @Nested
  @DisplayName("isPassConditionMet 会签判定")
  class PassCondition {

    @Test
    @DisplayName("OR：任意 1 人通过即满足")
    void orSign() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(Map.of("performType", "OR"));
      assertThat(config.isPassConditionMet(1, 5)).isTrue();
      assertThat(config.isPassConditionMet(0, 5)).isFalse();
    }

    @Test
    @DisplayName("PARALLEL：通过人数 ≥ min(approveCount, totalCount)")
    void parallelSign() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(
              Map.of("performType", "PARALLEL", "approveCount", 3));
      assertThat(config.isPassConditionMet(3, 5)).isTrue();
      assertThat(config.isPassConditionMet(2, 5)).isFalse();
      assertThat(config.isPassConditionMet(3, 3)).isTrue();
      assertThat(config.isPassConditionMet(2, 3)).isFalse();
    }

    @Test
    @DisplayName("WEIGHTED：isPassConditionMet 固定返回 false（需用权重方法）")
    void weightedUsesWeightMethod() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(Map.of("performType", "WEIGHTED"));
      assertThat(config.isPassConditionMet(5, 5)).isFalse();
    }
  }

  @Nested
  @DisplayName("isWeightPassConditionMet 票签判定")
  class WeightCondition {

    @Test
    @DisplayName("WEIGHTED：通过率 ≥ votePassRate 时满足")
    void weightedPass() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(
              Map.of("performType", "WEIGHTED", "votePassRate", 0.5));
      assertThat(config.isWeightPassConditionMet(50, 100)).isTrue();
      assertThat(config.isWeightPassConditionMet(49, 100)).isFalse();
    }

    @Test
    @DisplayName("WEIGHTED：totalWeight ≤0 时返回 false")
    void zeroTotalWeight() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(Map.of("performType", "WEIGHTED"));
      assertThat(config.isWeightPassConditionMet(0, 0)).isFalse();
    }

    @Test
    @DisplayName("非 WEIGHTED 时退化为 isPassConditionMet")
    void nonWeightedDelegates() {
      CountersignConfigVO config =
          CountersignConfigVO.fromExt(Map.of("performType", "OR"));
      assertThat(config.isWeightPassConditionMet(1, 5)).isTrue();
    }
  }

  @Nested
  @DisplayName("模式判定")
  class ModeCheck {

    @Test
    @DisplayName("isOrSign / isParallel / isWeighted")
    void modeChecks() {
      assertThat(
              CountersignConfigVO.fromExt(Map.of("performType", "OR")).isOrSign())
          .isTrue();
      assertThat(
              CountersignConfigVO.fromExt(Map.of("performType", "PARALLEL")).isParallel())
          .isTrue();
      assertThat(
              CountersignConfigVO.fromExt(Map.of("performType", "WEIGHTED")).isWeighted())
          .isTrue();
    }
  }
}
