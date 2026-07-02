package com.njydsz.pmis.user.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssignmentStatus 状态机测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AssignmentStatus 资源分配状态机")
class AssignmentStatusTest {

    @Test
    @DisplayName("RELEASED / CANCELLED 终态")
    void terminal() {
        assertThat(AssignmentStatus.RELEASED.isTerminal()).isTrue();
        assertThat(AssignmentStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(AssignmentStatus.RESERVED.isTerminal()).isFalse();
        assertThat(AssignmentStatus.ACTIVE.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("RESERVED 可入 ACTIVE 或 CANCELLED")
    void reservedTransitions() {
        assertThat(AssignmentStatus.RESERVED.canTransitTo(AssignmentStatus.ACTIVE)).isTrue();
        assertThat(AssignmentStatus.RESERVED.canTransitTo(AssignmentStatus.CANCELLED)).isTrue();
        assertThat(AssignmentStatus.RESERVED.canTransitTo(AssignmentStatus.RELEASED)).isFalse();
    }

    @Test
    @DisplayName("ACTIVE 可调岗 / 离场")
    void activeTransitions() {
        assertThat(AssignmentStatus.ACTIVE.canTransitTo(AssignmentStatus.TRANSFERRING)).isTrue();
        assertThat(AssignmentStatus.ACTIVE.canTransitTo(AssignmentStatus.RELEASED)).isTrue();
        assertThat(AssignmentStatus.ACTIVE.canTransitTo(AssignmentStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("TRANSFERRING 回到 ACTIVE 或 离场")
    void transferringTransitions() {
        assertThat(AssignmentStatus.TRANSFERRING.canTransitTo(AssignmentStatus.ACTIVE)).isTrue();
        assertThat(AssignmentStatus.TRANSFERRING.canTransitTo(AssignmentStatus.RELEASED)).isTrue();
        assertThat(AssignmentStatus.TRANSFERRING.canTransitTo(AssignmentStatus.RESERVED)).isFalse();
    }

    @Test
    @DisplayName("终态不可再流转")
    void terminalNoTransit() {
        assertThat(AssignmentStatus.RELEASED.canTransitTo(AssignmentStatus.ACTIVE)).isFalse();
        assertThat(AssignmentStatus.CANCELLED.canTransitTo(AssignmentStatus.RESERVED)).isFalse();
    }

    @Test
    @DisplayName("fromCode 解析")
    void fromCode() {
        assertThat(AssignmentStatus.fromCode("ACTIVE")).isEqualTo(AssignmentStatus.ACTIVE);
        assertThat(AssignmentStatus.fromCode("released")).isEqualTo(AssignmentStatus.RELEASED);
        assertThat(AssignmentStatus.fromCode("XXX")).isNull();
        assertThat(AssignmentStatus.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("自流转允许（save 时同状态）")
    void selfTransit() {
        assertThat(AssignmentStatus.ACTIVE.canTransitTo(AssignmentStatus.ACTIVE)).isTrue();
    }
}
