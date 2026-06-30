package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpportunityStatus 状态机测试")
class OpportunityStatusTest {

    @Test
    @DisplayName("终态判定")
    void isTerminal() {
        assertThat(OpportunityStatus.WON.isTerminal()).isTrue();
        assertThat(OpportunityStatus.LOST.isTerminal()).isTrue();
        assertThat(OpportunityStatus.INVALID.isTerminal()).isTrue();
        assertThat(OpportunityStatus.FOLLOWING.isTerminal()).isFalse();
        assertThat(OpportunityStatus.QUOTED.isTerminal()).isFalse();
        assertThat(OpportunityStatus.NEGOTIATING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法状态迁移")
    void canTransit() {
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.QUOTED)).isTrue();
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.NEGOTIATING)).isTrue();
        assertThat(OpportunityStatus.QUOTED.canTransitTo(OpportunityStatus.NEGOTIATING)).isTrue();
        assertThat(OpportunityStatus.NEGOTIATING.canTransitTo(OpportunityStatus.WON)).isTrue();
        // 任何非终态 -> LOST/INVALID
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.LOST)).isTrue();
        assertThat(OpportunityStatus.QUOTED.canTransitTo(OpportunityStatus.INVALID)).isTrue();
    }

    @Test
    @DisplayName("非法状态迁移")
    void cannotTransit() {
        assertThat(OpportunityStatus.WON.canTransitTo(OpportunityStatus.FOLLOWING)).isFalse();
        assertThat(OpportunityStatus.LOST.canTransitTo(OpportunityStatus.WON)).isFalse();
        assertThat(OpportunityStatus.INVALID.canTransitTo(OpportunityStatus.QUOTED)).isFalse();
        // 不能跳过中间阶段
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.WON)).isFalse();
        assertThat(OpportunityStatus.QUOTED.canTransitTo(OpportunityStatus.FOLLOWING)).isFalse();
    }

    @Test
    @DisplayName("fromCode 容错")
    void fromCode() {
        assertThat(OpportunityStatus.fromCode("WON")).isEqualTo(OpportunityStatus.WON);
        assertThat(OpportunityStatus.fromCode("won")).isEqualTo(OpportunityStatus.WON);
        assertThat(OpportunityStatus.fromCode(null)).isNull();
        assertThat(OpportunityStatus.fromCode("UNKNOWN")).isNull();
    }
}
