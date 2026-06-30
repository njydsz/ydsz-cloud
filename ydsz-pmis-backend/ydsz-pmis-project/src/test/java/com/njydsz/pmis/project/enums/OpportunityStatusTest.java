package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpportunityStatus 状态机测试
 */
@DisplayName("OpportunityStatus 商机状态机测试")
class OpportunityStatusTest {

    @Test
    @DisplayName("CONVERTED/LOST/INVALID 是终态")
    void terminalStates() {
        assertThat(OpportunityStatus.CONVERTED.isTerminal()).isTrue();
        assertThat(OpportunityStatus.LOST.isTerminal()).isTrue();
        assertThat(OpportunityStatus.INVALID.isTerminal()).isTrue();
        assertThat(OpportunityStatus.FOLLOWING.isTerminal()).isFalse();
        assertThat(OpportunityStatus.QUOTED.isTerminal()).isFalse();
        assertThat(OpportunityStatus.NEGOTIATING.isTerminal()).isFalse();
        assertThat(OpportunityStatus.WON.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("FOLLOWING 可到 QUOTED/NEGOTIATING/LOST/INVALID")
    void followingTransition() {
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.QUOTED)).isTrue();
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.NEGOTIATING)).isTrue();
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.LOST)).isTrue();
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.INVALID)).isTrue();
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.WON)).isFalse();
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(OpportunityStatus.CONVERTED)).isFalse();
    }

    @Test
    @DisplayName("QUOTED 可到 NEGOTIATING/WON/LOST/INVALID")
    void quotedTransition() {
        assertThat(OpportunityStatus.QUOTED.canTransitTo(OpportunityStatus.NEGOTIATING)).isTrue();
        assertThat(OpportunityStatus.QUOTED.canTransitTo(OpportunityStatus.WON)).isTrue();
        assertThat(OpportunityStatus.QUOTED.canTransitTo(OpportunityStatus.LOST)).isTrue();
        assertThat(OpportunityStatus.QUOTED.canTransitTo(OpportunityStatus.INVALID)).isTrue();
        assertThat(OpportunityStatus.QUOTED.canTransitTo(OpportunityStatus.CONVERTED)).isFalse();
    }

    @Test
    @DisplayName("NEGOTIATING 可到 WON/LOST/INVALID")
    void negotiatingTransition() {
        assertThat(OpportunityStatus.NEGOTIATING.canTransitTo(OpportunityStatus.WON)).isTrue();
        assertThat(OpportunityStatus.NEGOTIATING.canTransitTo(OpportunityStatus.LOST)).isTrue();
        assertThat(OpportunityStatus.NEGOTIATING.canTransitTo(OpportunityStatus.INVALID)).isTrue();
        assertThat(OpportunityStatus.NEGOTIATING.canTransitTo(OpportunityStatus.QUOTED)).isFalse();
        assertThat(OpportunityStatus.NEGOTIATING.canTransitTo(OpportunityStatus.CONVERTED)).isFalse();
    }

    @Test
    @DisplayName("WON 正常情况下可到 CONVERTED；非终态回退规则允许 WON→LOST/INVALID")
    void wonTransition() {
        assertThat(OpportunityStatus.WON.canTransitTo(OpportunityStatus.CONVERTED)).isTrue();
        // 业务规则：任何非终态可回退到 LOST/INVALID
        assertThat(OpportunityStatus.WON.canTransitTo(OpportunityStatus.LOST)).isTrue();
        assertThat(OpportunityStatus.WON.canTransitTo(OpportunityStatus.INVALID)).isTrue();
        // WON 不能再回到 NEGOTIATING/QUOTED
        assertThat(OpportunityStatus.WON.canTransitTo(OpportunityStatus.NEGOTIATING)).isFalse();
        assertThat(OpportunityStatus.WON.canTransitTo(OpportunityStatus.QUOTED)).isFalse();
    }

    @Test
    @DisplayName("CONVERTED 是完全终态")
    void convertedTerminal() {
        for (OpportunityStatus s : OpportunityStatus.values()) {
            if (s == OpportunityStatus.CONVERTED) continue;
            assertThat(OpportunityStatus.CONVERTED.canTransitTo(s)).isFalse();
        }
    }

    @Test
    @DisplayName("LOST 是终态")
    void lostTerminal() {
        for (OpportunityStatus s : OpportunityStatus.values()) {
            if (s == OpportunityStatus.LOST) continue;
            assertThat(OpportunityStatus.LOST.canTransitTo(s)).isFalse();
        }
    }

    @Test
    @DisplayName("INVALID 是终态")
    void invalidTerminal() {
        for (OpportunityStatus s : OpportunityStatus.values()) {
            if (s == OpportunityStatus.INVALID) continue;
            assertThat(OpportunityStatus.INVALID.canTransitTo(s)).isFalse();
        }
    }

    @Test
    @DisplayName("canTransitTo null 应返回 false")
    void nullTarget() {
        assertThat(OpportunityStatus.FOLLOWING.canTransitTo(null)).isFalse();
    }

    @Test
    @DisplayName("fromCode 解析")
    void fromCode() {
        assertThat(OpportunityStatus.fromCode("FOLLOWING")).isEqualTo(OpportunityStatus.FOLLOWING);
        assertThat(OpportunityStatus.fromCode("following")).isEqualTo(OpportunityStatus.FOLLOWING);
        assertThat(OpportunityStatus.fromCode("XXX")).isNull();
        assertThat(OpportunityStatus.fromCode(null)).isNull();
    }
}
