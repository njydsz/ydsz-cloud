package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交付物状态机测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DeliveryItemStatus 交付物状态机")
class DeliveryItemStatusTest {

    @Test
    @DisplayName("终态")
    void terminal() {
        assertThat(DeliveryItemStatus.ACCEPTED.isTerminal()).isTrue();
        assertThat(DeliveryItemStatus.WAIVED.isTerminal()).isTrue();
        assertThat(DeliveryItemStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("PENDING->SUBMITTED/WAIVED")
    void pendingTrans() {
        assertThat(DeliveryItemStatus.PENDING.canTransitTo(DeliveryItemStatus.SUBMITTED)).isTrue();
        assertThat(DeliveryItemStatus.PENDING.canTransitTo(DeliveryItemStatus.WAIVED)).isTrue();
        assertThat(DeliveryItemStatus.PENDING.canTransitTo(DeliveryItemStatus.ACCEPTED)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED 多目标")
    void submitted() {
        assertThat(DeliveryItemStatus.SUBMITTED.canTransitTo(DeliveryItemStatus.UNDER_REVIEW)).isTrue();
        assertThat(DeliveryItemStatus.SUBMITTED.canTransitTo(DeliveryItemStatus.ACCEPTED)).isTrue();
        assertThat(DeliveryItemStatus.SUBMITTED.canTransitTo(DeliveryItemStatus.REJECTED)).isTrue();
    }

    @Test
    @DisplayName("REJECTED->SUBMITTED/WAIVED")
    void rejected() {
        assertThat(DeliveryItemStatus.REJECTED.canTransitTo(DeliveryItemStatus.SUBMITTED)).isTrue();
        assertThat(DeliveryItemStatus.REJECTED.canTransitTo(DeliveryItemStatus.WAIVED)).isTrue();
        assertThat(DeliveryItemStatus.REJECTED.canTransitTo(DeliveryItemStatus.ACCEPTED)).isFalse();
    }

    @Test
    @DisplayName("终态不可迁移")
    void terminalNoTrans() {
        assertThat(DeliveryItemStatus.ACCEPTED.canTransitTo(DeliveryItemStatus.SUBMITTED)).isFalse();
        assertThat(DeliveryItemStatus.WAIVED.canTransitTo(DeliveryItemStatus.SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("fromCode")
    void fromCode() {
        assertThat(DeliveryItemStatus.fromCode("PENDING")).isEqualTo(DeliveryItemStatus.PENDING);
        assertThat(DeliveryItemStatus.fromCode(null)).isNull();
    }
}
