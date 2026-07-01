package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.enums.ClosureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ClosureAdmissionValidator 结项准入校验
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ClosureAdmissionValidator 结项准入")
class ClosureAdmissionValidatorTest {

    @Test
    @DisplayName("类型空")
    void nullType() {
        ClosureAdmissionValidator.AdmissionCheck r =
                ClosureAdmissionValidator.check(null, metrics("0.95", "1.0", "100", "0.2", "1000"));
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("指标空")
    void nullMetrics() {
        ClosureAdmissionValidator.AdmissionCheck r =
                ClosureAdmissionValidator.check(ClosureType.FORMAL, null);
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("FORCED 不校验")
    void forcedSkip() {
        ClosureAdmissionValidator.AdmissionCheck r =
                ClosureAdmissionValidator.check(ClosureType.FORCED, metrics("0.0", "0.0", "0", "0", "0"));
        assertThat(r.passed()).isTrue();
        assertThat(r.specialApprovalRequired()).isTrue();
    }

    @Test
    @DisplayName("FORMAL 通过")
    void formalOk() {
        ClosureAdmissionValidator.AdmissionCheck r = ClosureAdmissionValidator.check(
                ClosureType.FORMAL, metrics("0.96", "1.0", "100", "0.2", "1000"));
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("FORMAL 回款不足")
    void formalLowReceived() {
        ClosureAdmissionValidator.AdmissionCheck r = ClosureAdmissionValidator.check(
                ClosureType.FORMAL, metrics("0.50", "1.0", "100", "0.2", "1000"));
        assertThat(r.passed()).isFalse();
        assertThat(r.messages()).anyMatch(s -> s.contains("回款"));
    }

    @Test
    @DisplayName("FORMAL CPI 不足")
    void formalLowCpi() {
        ClosureAdmissionValidator.AdmissionCheck r = ClosureAdmissionValidator.check(
                ClosureType.FORMAL, metrics("0.96", "0.90", "100", "0.2", "1000"));
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("FORMAL 进度不足")
    void formalLowProgress() {
        ClosureAdmissionValidator.AdmissionCheck r = ClosureAdmissionValidator.check(
                ClosureType.FORMAL, metrics("0.96", "1.0", "90", "0.2", "1000"));
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("FORMAL 毛利率为负")
    void formalNegativeMargin() {
        ClosureAdmissionValidator.AdmissionCheck r = ClosureAdmissionValidator.check(
                ClosureType.FORMAL, metrics("0.96", "1.0", "100", "-0.1", "1000"));
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("FORMAL 交付物未全部通过")
    void formalDeliveryFail() {
        ClosureAdmissionValidator.AdmissionCheck r = ClosureAdmissionValidator.check(
                ClosureType.FORMAL, new ClosureAdmissionValidator.ClosureMetrics(
                        new BigDecimal("0.96"), new BigDecimal("1.0"), new BigDecimal("100"),
                        new BigDecimal("0.2"), new BigDecimal("1000"), true, false));
        assertThat(r.passed()).isFalse();
    }

    @Test
    @DisplayName("PRE_CLOSURE 通过")
    void preOk() {
        ClosureAdmissionValidator.AdmissionCheck r = ClosureAdmissionValidator.check(
                ClosureType.PRE_CLOSURE, metrics("0.65", "0.90", "85", "0.1", "1000"));
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("PRE_CLOSURE 回款不足")
    void preLowReceived() {
        ClosureAdmissionValidator.AdmissionCheck r = ClosureAdmissionValidator.check(
                ClosureType.PRE_CLOSURE, metrics("0.50", "0.90", "85", "0.1", "1000"));
        assertThat(r.passed()).isFalse();
    }

    private ClosureAdmissionValidator.ClosureMetrics metrics(String received, String cpi, String progress,
                                                              String margin, String cost) {
        return new ClosureAdmissionValidator.ClosureMetrics(
                new BigDecimal(received), new BigDecimal(cpi), new BigDecimal(progress),
                new BigDecimal(margin), new BigDecimal(cost), true, true);
    }
}
