package com.njydsz.pmis.project.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentDO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PaymentDO 测试")
class PaymentDOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter/getter 赋值应正确")
        void shouldSetAndGetFields() {
            PaymentDO entity = new PaymentDO();
            entity.setId(1L);
            entity.setPaymentNo("PAY-NO-001");
            entity.setPaymentCode("PAY-001");
            entity.setContractId(10L);
            entity.setInitiationId(20L);
            entity.setCustomerId(100L);
            entity.setCustomerName("测试客户");
            entity.setAmount(new BigDecimal("300000.00"));
            entity.setCurrency("CNY");
            entity.setPaymentMethod("BANK_TRANSFER");
            entity.setPaymentDate(LocalDate.of(2026, 4, 15));
            entity.setBankAccount("6222021234567890");
            entity.setOurBankAccount("6222029876543210");
            entity.setBankReference("REF-001");
            entity.setInvoiceAllocation("INV-001,INV-002");
            entity.setAllocatedAmount(new BigDecimal("300000.00"));
            entity.setUnallocatedAmount(BigDecimal.ZERO);
            entity.setStatus("CONFIRMED");
            entity.setRemark("测试备注");
            entity.setConfirmedBy(400L);
            entity.setConfirmedAt(LocalDateTime.of(2026, 4, 16, 10, 0));
            entity.setRecordedBy(300L);
            entity.setTenantId(1L);
            entity.setProviderTraceId("trace-001");
            entity.setVersion(1);
            entity.setCreatedAt(LocalDateTime.of(2026, 4, 15, 9, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 4, 16, 10, 0));
            entity.setDeleted(0);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getPaymentNo()).isEqualTo("PAY-NO-001");
            assertThat(entity.getPaymentCode()).isEqualTo("PAY-001");
            assertThat(entity.getContractId()).isEqualTo(10L);
            assertThat(entity.getInitiationId()).isEqualTo(20L);
            assertThat(entity.getCustomerId()).isEqualTo(100L);
            assertThat(entity.getCustomerName()).isEqualTo("测试客户");
            assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(entity.getCurrency()).isEqualTo("CNY");
            assertThat(entity.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
            assertThat(entity.getPaymentDate()).isEqualTo(LocalDate.of(2026, 4, 15));
            assertThat(entity.getBankAccount()).isEqualTo("6222021234567890");
            assertThat(entity.getOurBankAccount()).isEqualTo("6222029876543210");
            assertThat(entity.getBankReference()).isEqualTo("REF-001");
            assertThat(entity.getInvoiceAllocation()).isEqualTo("INV-001,INV-002");
            assertThat(entity.getAllocatedAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(entity.getUnallocatedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(entity.getStatus()).isEqualTo("CONFIRMED");
            assertThat(entity.getRemark()).isEqualTo("测试备注");
            assertThat(entity.getConfirmedBy()).isEqualTo(400L);
            assertThat(entity.getConfirmedAt()).isEqualTo(LocalDateTime.of(2026, 4, 16, 10, 0));
            assertThat(entity.getRecordedBy()).isEqualTo(300L);
            assertThat(entity.getTenantId()).isEqualTo(1L);
            assertThat(entity.getProviderTraceId()).isEqualTo("trace-001");
            assertThat(entity.getVersion()).isEqualTo(1);
            assertThat(entity.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 15, 9, 0));
            assertThat(entity.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 16, 10, 0));
            assertThat(entity.getDeleted()).isEqualTo(0);
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            PaymentDO entity = new PaymentDO();
            assertThat(entity.getId()).isNull();
            assertThat(entity.getPaymentNo()).isNull();
            assertThat(entity.getPaymentCode()).isNull();
            assertThat(entity.getContractId()).isNull();
            assertThat(entity.getInitiationId()).isNull();
            assertThat(entity.getCustomerId()).isNull();
            assertThat(entity.getAmount()).isNull();
            assertThat(entity.getCurrency()).isNull();
            assertThat(entity.getPaymentMethod()).isNull();
            assertThat(entity.getPaymentDate()).isNull();
            assertThat(entity.getStatus()).isNull();
            assertThat(entity.getVersion()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
            assertThat(entity.getDeleted()).isNull();
        }
    }

    @Nested
    @DisplayName("业务字段")
    class BusinessFields {

        @Test
        @DisplayName("回款金额应正确设置 BigDecimal")
        void shouldSetBigDecimalAmounts() {
            PaymentDO entity = new PaymentDO();
            entity.setAmount(new BigDecimal("500000.00"));
            entity.setAllocatedAmount(new BigDecimal("300000.00"));
            entity.setUnallocatedAmount(new BigDecimal("200000.00"));

            assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
            assertThat(entity.getAllocatedAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(entity.getUnallocatedAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
        }

        @Test
        @DisplayName("回款日期应正确设置")
        void shouldSetPaymentDate() {
            PaymentDO entity = new PaymentDO();
            LocalDate date = LocalDate.of(2026, 6, 15);
            entity.setPaymentDate(date);
            assertThat(entity.getPaymentDate()).isEqualTo(date);
        }

        @Test
        @DisplayName("回款状态应正确设置")
        void shouldSetPaymentStatus() {
            PaymentDO entity = new PaymentDO();
            entity.setStatus("PENDING");
            assertThat(entity.getStatus()).isEqualTo("PENDING");

            entity.setStatus("CONFIRMED");
            assertThat(entity.getStatus()).isEqualTo("CONFIRMED");
        }
    }
}