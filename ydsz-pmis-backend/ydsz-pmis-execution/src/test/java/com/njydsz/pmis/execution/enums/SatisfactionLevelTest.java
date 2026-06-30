package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SatisfactionLevel 满意度等级测试")
class SatisfactionLevelTest {

    @Test
    @DisplayName("5 星映射到非常满意")
    void fromScore5() {
        assertThat(SatisfactionLevel.fromScore(5)).isEqualTo(SatisfactionLevel.VERY_SATISFIED);
    }

    @Test
    @DisplayName("1 星映射到非常不满意")
    void fromScore1() {
        assertThat(SatisfactionLevel.fromScore(1)).isEqualTo(SatisfactionLevel.VERY_DISSATISFIED);
    }

    @Test
    @DisplayName("3 星映射到一般")
    void fromScore3() {
        assertThat(SatisfactionLevel.fromScore(3)).isEqualTo(SatisfactionLevel.NEUTRAL);
    }

    @Test
    @DisplayName("非法评分返回 null")
    void fromScoreInvalid() {
        assertThat(SatisfactionLevel.fromScore(0)).isNull();
        assertThat(SatisfactionLevel.fromScore(6)).isNull();
        assertThat(SatisfactionLevel.fromScore(null)).isNull();
    }

    @Test
    @DisplayName("score 字段与枚举一致")
    void scoreField() {
        assertThat(SatisfactionLevel.VERY_SATISFIED.getScore()).isEqualTo(5);
        assertThat(SatisfactionLevel.SATISFIED.getScore()).isEqualTo(4);
        assertThat(SatisfactionLevel.NEUTRAL.getScore()).isEqualTo(3);
        assertThat(SatisfactionLevel.DISSATISFIED.getScore()).isEqualTo(2);
        assertThat(SatisfactionLevel.VERY_DISSATISFIED.getScore()).isEqualTo(1);
    }

    @Test
    @DisplayName("fromCode 忽略大小写")
    void fromCode() {
        assertThat(SatisfactionLevel.fromCode("satisfied")).isEqualTo(SatisfactionLevel.SATISFIED);
        assertThat(SatisfactionLevel.fromCode("VERY_DISSATISFIED"))
                .isEqualTo(SatisfactionLevel.VERY_DISSATISFIED);
        assertThat(SatisfactionLevel.fromCode(null)).isNull();
    }
}
