package com.njydsz.pmis.project.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InvoiceCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("InvoiceCreateDTO 测试")
class InvoiceCreateDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter 赋值应正确")
        void shouldSetAndGetFields() {
            InvoiceCreateDTO dto = new InvoiceCreateDTO();
            dto.setInvoiceNo("INV-001");
            dto.setInvoiceCode("INV-CODE-001");
            dto.setInvoiceType("NORMAL");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setCustomerName("测试客户");
            dto.setInvoiceBasis("MILESTONE");
            dto.setAmount(new BigDecimal("500000.00"));
            dto.setTaxRate(new BigDecimal("0.13"));
            dto.setTaxAmount(new BigDecimal("65000.00"));
            dto.setNetAmount(new BigDecimal("435000.00"));
            dto.setCurrency("CNY");
            dto.setInvoiceDate(LocalDate.of(2026, 5, 1));
            dto.setTaxPeriod("2026-05");
            dto.setTitle("测试公司");
            dto.setTaxNo("123456789012345");
            dto.setBankInfo("中国银行");
            dto.setAddress("北京市");
            dto.setPhone("13800138000");
            dto.setRemark("测试备注");
            dto.setReversedById(null);
            dto.setOutsourcingProofId("proof-001");
            dto.setAcceptanceProofId("accept-001");
            dto.setAttachmentId("attach-001");
            dto.setAppliedBy(300L);

            assertThat(dto.getInvoiceNo()).isEqualTo("INV-001");
            assertThat(dto.getInvoiceCode()).isEqualTo("INV-CODE-001");
            assertThat(dto.getInvoiceType()).isEqualTo("NORMAL");
            assertThat(dto.getContractId()).isEqualTo(1L);
            assertThat(dto.getInitiationId()).isEqualTo(2L);
            assertThat(dto.getCustomerId()).isEqualTo(100L);
            assertThat(dto.getCustomerName()).isEqualTo("测试客户");
            assertThat(dto.getInvoiceBasis()).isEqualTo("MILESTONE");
            assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
            assertThat(dto.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.13"));
            assertThat(dto.getCurrency()).isEqualTo("CNY");
            assertThat(dto.getInvoiceDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(dto.getTitle()).isEqualTo("测试公司");
            assertThat(dto.getRemark()).isEqualTo("测试备注");
            assertThat(dto.getReversedById()).isNull();
            assertThat(dto.getAppliedBy()).isEqualTo(300L);
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            InvoiceCreateDTO dto = new InvoiceCreateDTO();
            assertThat(dto.getInvoiceNo()).isNull();
            assertThat(dto.getInvoiceCode()).isNull();
            assertThat(dto.getInvoiceType()).isNull();
            assertThat(dto.getContractId()).isNull();
            assertThat(dto.getInitiationId()).isNull();
            assertThat(dto.getCustomerId()).isNull();
            assertThat(dto.getAmount()).isNull();
        }

        @Test
        @DisplayName("currency 默认值应为 CNY")
        void shouldDefaultCurrencyToCNY() {
            InvoiceCreateDTO dto = new InvoiceCreateDTO();
            assertThat(dto.getCurrency()).isEqualTo("CNY");
        }
    }

    @Nested
    @DisplayName("校验注解")
    class Validation {

        @Test
        @DisplayName("所有必填字段正确填写时应通过校验")
        void shouldPassValidationWhenAllRequiredFieldsPresent() {
            InvoiceCreateDTO dto = new InvoiceCreateDTO();
            dto.setInvoiceCode("INV-CODE-001");
            dto.setInvoiceType("NORMAL");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setInvoiceBasis("MILESTONE");
            dto.setAmount(new BigDecimal("500000.00"));

            Set<ConstraintViolation<InvoiceCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("invoiceCode 为空时应校验失败")
        void shouldFailWhenInvoiceCodeBlank() {
            InvoiceCreateDTO dto = new InvoiceCreateDTO();
            dto.setInvoiceCode("");
            dto.setInvoiceType("NORMAL");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setInvoiceBasis("MILESTONE");
            dto.setAmount(new BigDecimal("500000.00"));

            Set<ConstraintViolation<InvoiceCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("invoiceCode"));
        }

        @Test
        @DisplayName("invoiceType 为空时应校验失败")
        void shouldFailWhenInvoiceTypeBlank() {
            InvoiceCreateDTO dto = new InvoiceCreateDTO();
            dto.setInvoiceCode("INV-CODE-001");
            dto.setInvoiceType("");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setInvoiceBasis("MILESTONE");
            dto.setAmount(new BigDecimal("500000.00"));

            Set<ConstraintViolation<InvoiceCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("invoiceType"));
        }

        @Test
        @DisplayName("contractId 为 null 时应校验失败")
        void shouldFailWhenContractIdNull() {
            InvoiceCreateDTO dto = new InvoiceCreateDTO();
            dto.setInvoiceCode("INV-CODE-001");
            dto.setInvoiceType("NORMAL");
            dto.setContractId(null);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setInvoiceBasis("MILESTONE");
            dto.setAmount(new BigDecimal("500000.00"));

            Set<ConstraintViolation<InvoiceCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contractId"));
        }

        @Test
        @DisplayName("amount 为 null 时应校验失败")
        void shouldFailWhenAmountNull() {
            InvoiceCreateDTO dto = new InvoiceCreateDTO();
            dto.setInvoiceCode("INV-CODE-001");
            dto.setInvoiceType("NORMAL");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setInvoiceBasis("MILESTONE");
            dto.setAmount(null);

            Set<ConstraintViolation<InvoiceCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
        }
    }
}