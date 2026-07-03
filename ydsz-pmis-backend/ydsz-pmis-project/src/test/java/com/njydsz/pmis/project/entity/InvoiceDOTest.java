package com.njydsz.pmis.project.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InvoiceDO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("InvoiceDO 测试")
class InvoiceDOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter/getter 赋值应正确")
        void shouldSetAndGetFields() {
            InvoiceDO entity = new InvoiceDO();
            entity.setId(1L);
            entity.setInvoiceNo("INV-001");
            entity.setInvoiceCode("INV-CODE-001");
            entity.setInvoiceType("NORMAL");
            entity.setContractId(10L);
            entity.setInitiationId(20L);
            entity.setCustomerId(100L);
            entity.setCustomerName("测试客户");
            entity.setInvoiceBasis("MILESTONE");
            entity.setAmount(new BigDecimal("500000.00"));
            entity.setTaxAmount(new BigDecimal("65000.00"));
            entity.setNetAmount(new BigDecimal("435000.00"));
            entity.setTaxRate(new BigDecimal("0.13"));
            entity.setCurrency("CNY");
            entity.setInvoiceDate(LocalDate.of(2026, 5, 1));
            entity.setTaxPeriod(LocalDate.of(2026, 5, 1));
            entity.setTitle("测试公司");
            entity.setTaxNo("123456789012345");
            entity.setBankInfo("中国银行");
            entity.setAddress("北京市");
            entity.setPhone("13800138000");
            entity.setRemark("测试备注");
            entity.setStatus("APPROVED");
            entity.setReversedById(null);
            entity.setAttachmentId("attach-001");
            entity.setApprovalComment("同意");
            entity.setAppliedBy(300L);
            entity.setApprovedBy(400L);
            entity.setApprovedAt(LocalDateTime.of(2026, 5, 2, 10, 0));
            entity.setIssuedBy(500L);
            entity.setIssuedAt(LocalDateTime.of(2026, 5, 3, 14, 0));
            entity.setTenantId(1L);
            entity.setProviderTraceId("trace-001");
            entity.setVersion(1);
            entity.setCreatedAt(LocalDateTime.of(2026, 5, 1, 9, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 5, 3, 14, 0));
            entity.setDeleted(0);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getInvoiceNo()).isEqualTo("INV-001");
            assertThat(entity.getInvoiceCode()).isEqualTo("INV-CODE-001");
            assertThat(entity.getInvoiceType()).isEqualTo("NORMAL");
            assertThat(entity.getContractId()).isEqualTo(10L);
            assertThat(entity.getInitiationId()).isEqualTo(20L);
            assertThat(entity.getCustomerId()).isEqualTo(100L);
            assertThat(entity.getCustomerName()).isEqualTo("测试客户");
            assertThat(entity.getInvoiceBasis()).isEqualTo("MILESTONE");
            assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
            assertThat(entity.getTaxAmount()).isEqualByComparingTo(new BigDecimal("65000.00"));
            assertThat(entity.getNetAmount()).isEqualByComparingTo(new BigDecimal("435000.00"));
            assertThat(entity.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.13"));
            assertThat(entity.getCurrency()).isEqualTo("CNY");
            assertThat(entity.getInvoiceDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(entity.getTitle()).isEqualTo("测试公司");
            assertThat(entity.getTaxNo()).isEqualTo("123456789012345");
            assertThat(entity.getBankInfo()).isEqualTo("中国银行");
            assertThat(entity.getAddress()).isEqualTo("北京市");
            assertThat(entity.getPhone()).isEqualTo("13800138000");
            assertThat(entity.getRemark()).isEqualTo("测试备注");
            assertThat(entity.getStatus()).isEqualTo("APPROVED");
            assertThat(entity.getReversedById()).isNull();
            assertThat(entity.getAttachmentId()).isEqualTo("attach-001");
            assertThat(entity.getApprovalComment()).isEqualTo("同意");
            assertThat(entity.getAppliedBy()).isEqualTo(300L);
            assertThat(entity.getApprovedBy()).isEqualTo(400L);
            assertThat(entity.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 10, 0));
            assertThat(entity.getIssuedBy()).isEqualTo(500L);
            assertThat(entity.getIssuedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 14, 0));
            assertThat(entity.getTenantId()).isEqualTo(1L);
            assertThat(entity.getProviderTraceId()).isEqualTo("trace-001");
            assertThat(entity.getVersion()).isEqualTo(1);
            assertThat(entity.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 9, 0));
            assertThat(entity.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 3, 14, 0));
            assertThat(entity.getDeleted()).isEqualTo(0);
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            InvoiceDO entity = new InvoiceDO();
            assertThat(entity.getId()).isNull();
            assertThat(entity.getInvoiceNo()).isNull();
            assertThat(entity.getInvoiceCode()).isNull();
            assertThat(entity.getInvoiceType()).isNull();
            assertThat(entity.getContractId()).isNull();
            assertThat(entity.getInitiationId()).isNull();
            assertThat(entity.getCustomerId()).isNull();
            assertThat(entity.getAmount()).isNull();
            assertThat(entity.getTaxAmount()).isNull();
            assertThat(entity.getNetAmount()).isNull();
            assertThat(entity.getStatus()).isNull();
            assertThat(entity.getReversedById()).isNull();
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
        @DisplayName("发票金额字段应正确设置 BigDecimal")
        void shouldSetBigDecimalAmounts() {
            InvoiceDO entity = new InvoiceDO();
            entity.setAmount(new BigDecimal("100000.00"));
            entity.setTaxAmount(new BigDecimal("13000.00"));
            entity.setNetAmount(new BigDecimal("87000.00"));
            entity.setTaxRate(new BigDecimal("0.13"));

            assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
            assertThat(entity.getTaxAmount()).isEqualByComparingTo(new BigDecimal("13000.00"));
            assertThat(entity.getNetAmount()).isEqualByComparingTo(new BigDecimal("87000.00"));
            assertThat(entity.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.13"));
        }

        @Test
        @DisplayName("红冲发票关联应正确设置")
        void shouldSetRedReverseInvoice() {
            InvoiceDO entity = new InvoiceDO();
            entity.setInvoiceType("RED_REVERSE");
            entity.setReversedById(999L);

            assertThat(entity.getInvoiceType()).isEqualTo("RED_REVERSE");
            assertThat(entity.getReversedById()).isEqualTo(999L);
        }

        @Test
        @DisplayName("发票状态应正确设置")
        void shouldSetInvoiceStatus() {
            InvoiceDO entity = new InvoiceDO();
            entity.setStatus("DRAFT");
            assertThat(entity.getStatus()).isEqualTo("DRAFT");

            entity.setStatus("ISSUED");
            assertThat(entity.getStatus()).isEqualTo("ISSUED");
        }
    }
}