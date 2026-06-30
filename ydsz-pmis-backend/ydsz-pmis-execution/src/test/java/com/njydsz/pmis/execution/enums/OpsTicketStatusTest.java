package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpsTicketStatus 运维工单状态机测试")
class OpsTicketStatusTest {

    @Test
    @DisplayName("终态判定")
    void terminal() {
        assertThat(OpsTicketStatus.CLOSED.isTerminal()).isTrue();
        assertThat(OpsTicketStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OpsTicketStatus.OPEN.isTerminal()).isFalse();
        assertThat(OpsTicketStatus.ASSIGNED.isTerminal()).isFalse();
        assertThat(OpsTicketStatus.IN_PROGRESS.isTerminal()).isFalse();
        assertThat(OpsTicketStatus.RESOLVED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法迁移：主流程 OPEN→ASSIGNED→IN_PROGRESS→RESOLVED→CLOSED")
    void canTransit_mainFlow() {
        assertThat(OpsTicketStatus.OPEN.canTransitTo(OpsTicketStatus.ASSIGNED)).isTrue();
        assertThat(OpsTicketStatus.ASSIGNED.canTransitTo(OpsTicketStatus.IN_PROGRESS)).isTrue();
        assertThat(OpsTicketStatus.IN_PROGRESS.canTransitTo(OpsTicketStatus.RESOLVED)).isTrue();
        assertThat(OpsTicketStatus.RESOLVED.canTransitTo(OpsTicketStatus.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("合法迁移：RESOLVED→IN_PROGRESS 重新打开")
    void canTransit_reopen() {
        assertThat(OpsTicketStatus.RESOLVED.canTransitTo(OpsTicketStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    @DisplayName("合法迁移：任意非终态 → CANCELLED")
    void canTransit_cancel() {
        assertThat(OpsTicketStatus.OPEN.canTransitTo(OpsTicketStatus.CANCELLED)).isTrue();
        assertThat(OpsTicketStatus.ASSIGNED.canTransitTo(OpsTicketStatus.CANCELLED)).isTrue();
        assertThat(OpsTicketStatus.IN_PROGRESS.canTransitTo(OpsTicketStatus.CANCELLED)).isTrue();
        assertThat(OpsTicketStatus.RESOLVED.canTransitTo(OpsTicketStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("非法迁移：终态不能再迁移")
    void cannotTransitFromTerminal() {
        assertThat(OpsTicketStatus.CLOSED.canTransitTo(OpsTicketStatus.OPEN)).isFalse();
        assertThat(OpsTicketStatus.CANCELLED.canTransitTo(OpsTicketStatus.OPEN)).isFalse();
    }

    @Test
    @DisplayName("非法迁移：跳过中间状态")
    void cannotTransit_skipState() {
        assertThat(OpsTicketStatus.OPEN.canTransitTo(OpsTicketStatus.RESOLVED)).isFalse();
        assertThat(OpsTicketStatus.OPEN.canTransitTo(OpsTicketStatus.CLOSED)).isFalse();
    }

    @Test
    @DisplayName("fromCode 忽略大小写")
    void fromCode() {
        assertThat(OpsTicketStatus.fromCode("open")).isEqualTo(OpsTicketStatus.OPEN);
        assertThat(OpsTicketStatus.fromCode("CLOSED")).isEqualTo(OpsTicketStatus.CLOSED);
        assertThat(OpsTicketStatus.fromCode(null)).isNull();
        assertThat(OpsTicketStatus.fromCode("XXX")).isNull();
    }
}
