package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreditLevel 信用等级")
class CreditLevelTest {

    @Test
    @DisplayName("fromScore 区间映射")
    void fromScore() {
        assertThat(CreditLevel.fromScore(100)).isEqualTo(CreditLevel.A);
        assertThat(CreditLevel.fromScore(90)).isEqualTo(CreditLevel.A);
        assertThat(CreditLevel.fromScore(89)).isEqualTo(CreditLevel.B);
        assertThat(CreditLevel.fromScore(75)).isEqualTo(CreditLevel.B);
        assertThat(CreditLevel.fromScore(74)).isEqualTo(CreditLevel.C);
        assertThat(CreditLevel.fromScore(60)).isEqualTo(CreditLevel.C);
        assertThat(CreditLevel.fromScore(59)).isEqualTo(CreditLevel.D);
        assertThat(CreditLevel.fromScore(0)).isEqualTo(CreditLevel.D);
    }

    @Test
    @DisplayName("fromScore 负数钳制为 0")
    void fromScoreNegative() {
        assertThat(CreditLevel.fromScore(-1)).isEqualTo(CreditLevel.D);
    }

    @Test
    @DisplayName("fromCode")
    void fromCode() {
        assertThat(CreditLevel.fromCode("A")).isEqualTo(CreditLevel.A);
        assertThat(CreditLevel.fromCode("b")).isEqualTo(CreditLevel.B);
        assertThat(CreditLevel.fromCode("X")).isNull();
        assertThat(CreditLevel.fromCode(null)).isNull();
    }
}
