package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentStatus 回款状态机")
class PaymentStatusTest {

    @Test
    @DisplayName("fromCode")
    void fromCode() {
        assertThat(PaymentStatus.fromCode("PENDING")).isEqualTo(PaymentStatus.PENDING);
        assertThat(PaymentStatus.fromCode("Allocated")).isEqualTo(PaymentStatus.ALLOCATED);
        assertThat(PaymentStatus.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("isTerminal")
    void terminal() {
        assertThat(PaymentStatus.ALLOCATED.isTerminal()).isTrue();
        assertThat(PaymentStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(PaymentStatus.PENDING.isTerminal()).isFalse();
        assertThat(PaymentStatus.CONFIRMED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("PENDING → CONFIRMED/CANCELLED")
    void pendingAllowed() {
        assertThat(PaymentStatus.PENDING.canTransitTo(PaymentStatus.CONFIRMED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitTo(PaymentStatus.CANCELLED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitTo(PaymentStatus.ALLOCATED)).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED → ALLOCATED/CANCELLED")
    void confirmedAllowed() {
        assertThat(PaymentStatus.CONFIRMED.canTransitTo(PaymentStatus.ALLOCATED)).isTrue();
        assertThat(PaymentStatus.CONFIRMED.canTransitTo(PaymentStatus.CANCELLED)).isTrue();
        assertThat(PaymentStatus.CONFIRMED.canTransitTo(PaymentStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("ALLOCATED 是终态")
    void allocatedTerminal() {
        for (PaymentStatus t : PaymentStatus.values()) {
            if (t == PaymentStatus.ALLOCATED) continue;
            assertThat(PaymentStatus.ALLOCATED.canTransitTo(t)).isFalse();
        }
    }
}
