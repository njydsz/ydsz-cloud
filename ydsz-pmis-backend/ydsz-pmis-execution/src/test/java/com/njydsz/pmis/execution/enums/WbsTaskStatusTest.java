package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WbsTaskStatus 任务状态机测试")
class WbsTaskStatusTest {

    @Test
    @DisplayName("终态判定")
    void isTerminal() {
        assertThat(WbsTaskStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(WbsTaskStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(WbsTaskStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法迁移")
    void canTransit() {
        assertThat(WbsTaskStatus.PLANNED.canTransitTo(WbsTaskStatus.IN_PROGRESS)).isTrue();
        assertThat(WbsTaskStatus.IN_PROGRESS.canTransitTo(WbsTaskStatus.BLOCKED)).isTrue();
        assertThat(WbsTaskStatus.IN_PROGRESS.canTransitTo(WbsTaskStatus.IN_REVIEW)).isTrue();
        assertThat(WbsTaskStatus.IN_REVIEW.canTransitTo(WbsTaskStatus.COMPLETED)).isTrue();
        assertThat(WbsTaskStatus.BLOCKED.canTransitTo(WbsTaskStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    @DisplayName("非法迁移")
    void cannotTransit() {
        assertThat(WbsTaskStatus.COMPLETED.canTransitTo(WbsTaskStatus.IN_PROGRESS)).isFalse();
        assertThat(WbsTaskStatus.CANCELLED.canTransitTo(WbsTaskStatus.IN_PROGRESS)).isFalse();
        assertThat(WbsTaskStatus.PLANNED.canTransitTo(WbsTaskStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("fromCode")
    void fromCode() {
        assertThat(WbsTaskStatus.fromCode("in_progress")).isEqualTo(WbsTaskStatus.IN_PROGRESS);
        assertThat(WbsTaskStatus.fromCode(null)).isNull();
    }
}
