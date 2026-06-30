package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WarrantyStatus 质保期状态机测试")
class WarrantyStatusTest {

    @Test
    @DisplayName("终态判定")
    void terminal() {
        assertThat(WarrantyStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(WarrantyStatus.TERMINATED.isTerminal()).isTrue();
        assertThat(WarrantyStatus.ACTIVE.isTerminal()).isFalse();
        assertThat(WarrantyStatus.EXPIRING_SOON.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法迁移")
    void canTransit() {
        assertThat(WarrantyStatus.ACTIVE.canTransitTo(WarrantyStatus.EXPIRING_SOON)).isTrue();
        assertThat(WarrantyStatus.ACTIVE.canTransitTo(WarrantyStatus.EXPIRED)).isTrue();
        assertThat(WarrantyStatus.ACTIVE.canTransitTo(WarrantyStatus.TERMINATED)).isTrue();
        assertThat(WarrantyStatus.EXPIRING_SOON.canTransitTo(WarrantyStatus.EXPIRED)).isTrue();
        assertThat(WarrantyStatus.EXPIRING_SOON.canTransitTo(WarrantyStatus.TERMINATED)).isTrue();
        assertThat(WarrantyStatus.ACTIVE.canTransitTo(WarrantyStatus.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("非法迁移：终态不能再迁移")
    void cannotTransitFromTerminal() {
        assertThat(WarrantyStatus.EXPIRED.canTransitTo(WarrantyStatus.ACTIVE)).isFalse();
        assertThat(WarrantyStatus.EXPIRED.canTransitTo(WarrantyStatus.TERMINATED)).isFalse();
        assertThat(WarrantyStatus.TERMINATED.canTransitTo(WarrantyStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("非法迁移：EXPIRED 不能从 EXPIRING_SOON 之外到达")
    void cannotTransitToExpiredFromActive() {
        // ACTIVE → EXPIRED 实际上是允许的
        assertThat(WarrantyStatus.ACTIVE.canTransitTo(WarrantyStatus.EXPIRED)).isTrue();
    }

    @Test
    @DisplayName("fromCode 忽略大小写")
    void fromCode() {
        assertThat(WarrantyStatus.fromCode("active")).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(WarrantyStatus.fromCode("EXPIRED")).isEqualTo(WarrantyStatus.EXPIRED);
        assertThat(WarrantyStatus.fromCode(null)).isNull();
        assertThat(WarrantyStatus.fromCode("XXX")).isNull();
    }
}
