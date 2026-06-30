package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoiceType/InvoiceBasis 枚举")
class InvoiceTypeTest {

    @Test
    @DisplayName("InvoiceType.fromCode")
    void type() {
        assertThat(InvoiceType.fromCode("NORMAL")).isEqualTo(InvoiceType.NORMAL);
        assertThat(InvoiceType.fromCode("red_reverse")).isEqualTo(InvoiceType.RED_REVERSE);
        assertThat(InvoiceType.fromCode(null)).isNull();
        assertThat(InvoiceType.fromCode("X")).isNull();
    }

    @Test
    @DisplayName("InvoiceBasis.fromCode")
    void basis() {
        assertThat(InvoiceBasis.fromCode("MILESTONE")).isEqualTo(InvoiceBasis.MILESTONE);
        assertThat(InvoiceBasis.fromCode("OUTSOURCING")).isEqualTo(InvoiceBasis.OUTSOURCING);
        assertThat(InvoiceBasis.fromCode("FINAL")).isEqualTo(InvoiceBasis.FINAL);
        assertThat(InvoiceBasis.fromCode("X")).isNull();
    }
}
