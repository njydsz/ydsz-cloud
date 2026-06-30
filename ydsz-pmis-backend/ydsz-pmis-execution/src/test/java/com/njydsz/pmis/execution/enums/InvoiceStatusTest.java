package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoiceStatus 发票状态机")
class InvoiceStatusTest {

    @Test
    @DisplayName("fromCode - 大小写不敏感")
    void fromCode() {
        assertThat(InvoiceStatus.fromCode("draft")).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(InvoiceStatus.fromCode("ISSUED")).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(InvoiceStatus.fromCode(null)).isNull();
        assertThat(InvoiceStatus.fromCode("X")).isNull();
    }

    @Test
    @DisplayName("isTerminal")
    void terminal() {
        assertThat(InvoiceStatus.RED_REVERSED.isTerminal()).isTrue();
        assertThat(InvoiceStatus.CANCELLED.isTerminal()).isTrue();
        // ISSUED 可红冲，非纯终态
        assertThat(InvoiceStatus.ISSUED.isTerminal()).isFalse();
        assertThat(InvoiceStatus.DRAFT.isTerminal()).isFalse();
        assertThat(InvoiceStatus.SUBMITTED.isTerminal()).isFalse();
        assertThat(InvoiceStatus.APPROVED.isTerminal()).isFalse();
        assertThat(InvoiceStatus.REJECTED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("DRAFT → SUBMITTED/CANCELLED 合法")
    void draftAllowed() {
        assertThat(InvoiceStatus.DRAFT.canTransitTo(InvoiceStatus.SUBMITTED)).isTrue();
        assertThat(InvoiceStatus.DRAFT.canTransitTo(InvoiceStatus.CANCELLED)).isTrue();
        assertThat(InvoiceStatus.DRAFT.canTransitTo(InvoiceStatus.APPROVED)).isFalse();
        assertThat(InvoiceStatus.DRAFT.canTransitTo(InvoiceStatus.ISSUED)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED → APPROVED/REJECTED 合法")
    void submittedAllowed() {
        assertThat(InvoiceStatus.SUBMITTED.canTransitTo(InvoiceStatus.APPROVED)).isTrue();
        assertThat(InvoiceStatus.SUBMITTED.canTransitTo(InvoiceStatus.REJECTED)).isTrue();
        assertThat(InvoiceStatus.SUBMITTED.canTransitTo(InvoiceStatus.ISSUED)).isFalse();
    }

    @Test
    @DisplayName("APPROVED → ISSUED/CANCELLED 合法")
    void approvedAllowed() {
        assertThat(InvoiceStatus.APPROVED.canTransitTo(InvoiceStatus.ISSUED)).isTrue();
        assertThat(InvoiceStatus.APPROVED.canTransitTo(InvoiceStatus.CANCELLED)).isTrue();
        assertThat(InvoiceStatus.APPROVED.canTransitTo(InvoiceStatus.SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("ISSUED → RED_REVERSED 合法，且不能再开")
    void issuedAllowed() {
        assertThat(InvoiceStatus.ISSUED.canTransitTo(InvoiceStatus.RED_REVERSED)).isTrue();
        assertThat(InvoiceStatus.ISSUED.canTransitTo(InvoiceStatus.APPROVED)).isFalse();
        assertThat(InvoiceStatus.ISSUED.canTransitTo(InvoiceStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("REJECTED → DRAFT/SUBMITTED 合法")
    void rejectedAllowed() {
        assertThat(InvoiceStatus.REJECTED.canTransitTo(InvoiceStatus.DRAFT)).isTrue();
        assertThat(InvoiceStatus.REJECTED.canTransitTo(InvoiceStatus.SUBMITTED)).isTrue();
        assertThat(InvoiceStatus.REJECTED.canTransitTo(InvoiceStatus.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("终态不能再迁移")
    void terminalNoTransit() {
        for (InvoiceStatus t : new InvoiceStatus[]{InvoiceStatus.RED_REVERSED, InvoiceStatus.CANCELLED}) {
            for (InvoiceStatus target : InvoiceStatus.values()) {
                if (target == t) continue;
                assertThat(t.canTransitTo(target)).isFalse();
            }
        }
    }
}
