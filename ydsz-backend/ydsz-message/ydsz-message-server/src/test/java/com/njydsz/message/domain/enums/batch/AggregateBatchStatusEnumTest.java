package com.njydsz.message.domain.enums.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * AggregateBatchStatusEnum 聚合批次状态机单元测试。
 *
 * <p>P1-2: 验证 SENDING 中间态的状态流转规则。
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("AggregateBatchStatusEnum 聚合批次状态机测试")
class AggregateBatchStatusEnumTest {

    @Nested
    @DisplayName("canTransitTo() 状态流转校验")
    class CanTransitToTest {

        @Test
        @DisplayName("PENDING → READY/SENDING/CANCELLED 合法")
        void pendingCanTransitToReadySendingCancelled() {
            assertThat(AggregateBatchStatusEnum.PENDING.canTransitTo(AggregateBatchStatusEnum.READY)).isTrue();
            assertThat(AggregateBatchStatusEnum.PENDING.canTransitTo(AggregateBatchStatusEnum.SENDING)).isFalse();
            assertThat(AggregateBatchStatusEnum.PENDING.canTransitTo(AggregateBatchStatusEnum.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("READY → SENDING/CANCELLED 合法(P1-2 CAS 占有)")
        void readyCanTransitToSendingCancelled() {
            assertThat(AggregateBatchStatusEnum.READY.canTransitTo(AggregateBatchStatusEnum.SENDING)).isTrue();
            assertThat(AggregateBatchStatusEnum.READY.canTransitTo(AggregateBatchStatusEnum.SENT)).isFalse();
            assertThat(AggregateBatchStatusEnum.READY.canTransitTo(AggregateBatchStatusEnum.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("SENDING → SENT/READY/CANCELLED 合法(失败回退 READY)")
        void sendingCanTransitToSentReadyCancelled() {
            assertThat(AggregateBatchStatusEnum.SENDING.canTransitTo(AggregateBatchStatusEnum.SENT)).isTrue();
            assertThat(AggregateBatchStatusEnum.SENDING.canTransitTo(AggregateBatchStatusEnum.READY)).isTrue();
            assertThat(AggregateBatchStatusEnum.SENDING.canTransitTo(AggregateBatchStatusEnum.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("SENT/CANCELLED 终态不可流转")
        void terminalStatesCannotTransit() {
            assertThat(AggregateBatchStatusEnum.SENT.canTransitTo(AggregateBatchStatusEnum.PENDING)).isFalse();
            assertThat(AggregateBatchStatusEnum.SENT.canTransitTo(AggregateBatchStatusEnum.READY)).isFalse();
            assertThat(AggregateBatchStatusEnum.SENT.canTransitTo(AggregateBatchStatusEnum.SENDING)).isFalse();
            assertThat(AggregateBatchStatusEnum.CANCELLED.canTransitTo(AggregateBatchStatusEnum.PENDING)).isFalse();
            assertThat(AggregateBatchStatusEnum.CANCELLED.canTransitTo(AggregateBatchStatusEnum.READY)).isFalse();
            assertThat(AggregateBatchStatusEnum.CANCELLED.canTransitTo(AggregateBatchStatusEnum.SENDING)).isFalse();
        }

        @Test
        @DisplayName("自反: 同状态流转合法")
        void sameStateTransitAllowed() {
            for (AggregateBatchStatusEnum status : AggregateBatchStatusEnum.values()) {
                assertThat(status.canTransitTo(status))
                        .as("%s → %s 应合法", status, status)
                        .isTrue();
            }
        }
    }
}
