package com.njydsz.pmis.agent.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HitlApprovalStatus 枚举单元测试（P3-4 落地）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@DisplayName("HitlApprovalStatus 枚举测试")
class HitlApprovalStatusTest {

    @Nested
    @DisplayName("基本属性")
    class BasicTest {

        @Test
        @DisplayName("枚举值数量为 5")
        void shouldHaveFiveStatuses() {
            assertThat(HitlApprovalStatus.values()).hasSize(5);
        }

        @Test
        @DisplayName("编码与描述正确")
        void shouldHaveCorrectCodeAndDesc() {
            assertThat(HitlApprovalStatus.PENDING.getCode()).isEqualTo("PENDING");
            assertThat(HitlApprovalStatus.PENDING.getDesc()).isEqualTo("等待审批");
            assertThat(HitlApprovalStatus.APPROVED.getCode()).isEqualTo("APPROVED");
            assertThat(HitlApprovalStatus.REJECTED.getCode()).isEqualTo("已批准".replace("已批准", "REJECTED"));
        }
    }

    @Nested
    @DisplayName("终态判断")
    class TerminalTest {

        @Test
        @DisplayName("PENDING 非终态")
        void pendingIsNotTerminal() {
            assertThat(HitlApprovalStatus.PENDING.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("APPROVED/REJECTED/TIMEOUT/CANCELLED 均为终态")
        void terminalStatuses() {
            assertThat(HitlApprovalStatus.APPROVED.isTerminal()).isTrue();
            assertThat(HitlApprovalStatus.REJECTED.isTerminal()).isTrue();
            assertThat(HitlApprovalStatus.TIMEOUT.isTerminal()).isTrue();
            assertThat(HitlApprovalStatus.CANCELLED.isTerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("状态迁移校验")
    class TransitTest {

        @Test
        @DisplayName("PENDING 可迁移到任意终态")
        void pendingCanTransitToTerminal() {
            assertThat(HitlApprovalStatus.PENDING.canTransitTo(HitlApprovalStatus.APPROVED)).isTrue();
            assertThat(HitlApprovalStatus.PENDING.canTransitTo(HitlApprovalStatus.REJECTED)).isTrue();
            assertThat(HitlApprovalStatus.PENDING.canTransitTo(HitlApprovalStatus.TIMEOUT)).isTrue();
            assertThat(HitlApprovalStatus.PENDING.canTransitTo(HitlApprovalStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("终态不可迁移到其他状态")
        void terminalCannotTransit() {
            assertThat(HitlApprovalStatus.APPROVED.canTransitTo(HitlApprovalStatus.REJECTED)).isFalse();
            assertThat(HitlApprovalStatus.REJECTED.canTransitTo(HitlApprovalStatus.APPROVED)).isFalse();
            assertThat(HitlApprovalStatus.TIMEOUT.canTransitTo(HitlApprovalStatus.APPROVED)).isFalse();
        }

        @Test
        @DisplayName("相同状态可迁移（幂等）")
        void sameStatusCanTransit() {
            assertThat(HitlApprovalStatus.PENDING.canTransitTo(HitlApprovalStatus.PENDING)).isTrue();
            assertThat(HitlApprovalStatus.APPROVED.canTransitTo(HitlApprovalStatus.APPROVED)).isTrue();
        }

        @Test
        @DisplayName("null 目标返回 false")
        void nullTargetReturnsFalse() {
            assertThat(HitlApprovalStatus.PENDING.canTransitTo(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("审批结果判断")
    class ApprovedTest {

        @Test
        @DisplayName("仅 APPROVED 为已批准")
        void onlyApprovedIsApproved() {
            assertThat(HitlApprovalStatus.APPROVED.isApproved()).isTrue();
            assertThat(HitlApprovalStatus.PENDING.isApproved()).isFalse();
            assertThat(HitlApprovalStatus.REJECTED.isApproved()).isFalse();
            assertThat(HitlApprovalStatus.TIMEOUT.isApproved()).isFalse();
            assertThat(HitlApprovalStatus.CANCELLED.isApproved()).isFalse();
        }
    }

    @Nested
    @DisplayName("fromCode 解析")
    class FromCodeTest {

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertThat(HitlApprovalStatus.fromCode("pending")).isEqualTo(HitlApprovalStatus.PENDING);
            assertThat(HitlApprovalStatus.fromCode("APPROVED")).isEqualTo(HitlApprovalStatus.APPROVED);
            assertThat(HitlApprovalStatus.fromCode("Rejected")).isEqualTo(HitlApprovalStatus.REJECTED);
        }

        @Test
        @DisplayName("null 返回 null")
        void nullReturnsNull() {
            assertThat(HitlApprovalStatus.fromCode(null)).isNull();
        }

        @Test
        @DisplayName("未知编码返回 null")
        void unknownReturnsNull() {
            assertThat(HitlApprovalStatus.fromCode("UNKNOWN")).isNull();
        }
    }
}
