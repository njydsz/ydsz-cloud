package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimeEntryStatus 工时状态机测试")
class TimeEntryStatusTest {

    @Test
    @DisplayName("终态判定")
    void isTerminal() {
        assertThat(TimeEntryStatus.APPROVED.isTerminal()).isTrue();
        assertThat(TimeEntryStatus.REJECTED.isTerminal()).isTrue();
        assertThat(TimeEntryStatus.DRAFT.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法迁移")
    void canTransit() {
        assertThat(TimeEntryStatus.DRAFT.canTransitTo(TimeEntryStatus.SUBMITTED)).isTrue();
        assertThat(TimeEntryStatus.SUBMITTED.canTransitTo(TimeEntryStatus.APPROVED)).isTrue();
        assertThat(TimeEntryStatus.SUBMITTED.canTransitTo(TimeEntryStatus.REJECTED)).isTrue();
        assertThat(TimeEntryStatus.REJECTED.canTransitTo(TimeEntryStatus.DRAFT)).isTrue();
    }

    @Test
    @DisplayName("非法迁移")
    void cannotTransit() {
        assertThat(TimeEntryStatus.APPROVED.canTransitTo(TimeEntryStatus.DRAFT)).isFalse();
        assertThat(TimeEntryStatus.DRAFT.canTransitTo(TimeEntryStatus.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("fromCode")
    void fromCode() {
        assertThat(TimeEntryStatus.fromCode("approved")).isEqualTo(TimeEntryStatus.APPROVED);
        assertThat(TimeEntryStatus.fromCode(null)).isNull();
    }
}
