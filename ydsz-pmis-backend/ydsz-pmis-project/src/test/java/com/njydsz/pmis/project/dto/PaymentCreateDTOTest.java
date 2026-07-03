package com.njydsz.pmis.project.dto;

import jakarta.validation.ConstraintViolation;
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
 * PaymentCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PaymentCreateDTO 测试")
class PaymentCreateDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter 赋值应正确")
        void shouldSetAndGetFields() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            dto.setPaymentCode("PAY-001");
            dto.setPaymentNo("PAY-NO-001");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setCustomerName("测试客户");
            dto.setAmount(new BigDecimal("300000.00"));
            dto.setCurrency("CNY");
            dto.setPaymentMethod("BANK_TRANSFER");
            dto.setPaymentDate(LocalDate.of(2026, 4, 15));
            dto.setBankAccount("6222021234567890");
            dto.setOurBankAccount("6222029876543210");
            dto.setBankReference("REF-001");
            dto.setRemark("测试备注");
            dto.setInvoiceAllocation("INV-001,INV-002");
            dto.setAllocatedAmount(new BigDecimal("300000.00"));
            dto.setRecordedBy(300L);

            assertThat(dto.getPaymentCode()).isEqualTo("PAY-001");
            assertThat(dto.getPaymentNo()).isEqualTo("PAY-NO-001");
            assertThat(dto.getContractId()).isEqualTo(1L);
            assertThat(dto.getInitiationId()).isEqualTo(2L);
            assertThat(dto.getCustomerId()).isEqualTo(100L);
            assertThat(dto.getCustomerName()).isEqualTo("测试客户");
            assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(dto.getCurrency()).isEqualTo("CNY");
            assertThat(dto.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
            assertThat(dto.getPaymentDate()).isEqualTo(LocalDate.of(2026, 4, 15));
            assertThat(dto.getBankAccount()).isEqualTo("6222021234567890");
            assertThat(dto.getOurBankAccount()).isEqualTo("6222029876543210");
            assertThat(dto.getBankReference()).isEqualTo("REF-001");
            assertThat(dto.getRemark()).isEqualTo("测试备注");
            assertThat(dto.getInvoiceAllocation()).isEqualTo("INV-001,INV-002");
            assertThat(dto.getAllocatedAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(dto.getRecordedBy()).isEqualTo(300L);
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            assertThat(dto.getPaymentCode()).isNull();
            assertThat(dto.getPaymentNo()).isNull();
            assertThat(dto.getContractId()).isNull();
            assertThat(dto.getInitiationId()).isNull();
            assertThat(dto.getCustomerId()).isNull();
            assertThat(dto.getAmount()).isNull();
        }

        @Test
        @DisplayName("currency 默认值应为 CNY")
        void shouldDefaultCurrencyToCNY() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            assertThat(dto.getCurrency()).isEqualTo("CNY");
        }

        @Test
        @DisplayName("paymentMethod 默认值应为 BANK_TRANSFER")
        void shouldDefaultPaymentMethodToBankTransfer() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            assertThat(dto.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        }
    }

    @Nested
    @DisplayName("校验注解")
    class Validation {

        @Test
        @DisplayName("所有必填字段正确填写时应通过校验")
        void shouldPassValidationWhenAllRequiredFieldsPresent() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            dto.setPaymentCode("PAY-001");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setAmount(new BigDecimal("300000.00"));
            dto.setPaymentDate(LocalDate.of(2026, 4, 15));

            Set<ConstraintViolation<PaymentCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("paymentCode 为空时应校验失败")
        void shouldFailWhenPaymentCodeBlank() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            dto.setPaymentCode("");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setAmount(new BigDecimal("300000.00"));
            dto.setPaymentDate(LocalDate.of(2026, 4, 15));

            Set<ConstraintViolation<PaymentCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("paymentCode"));
        }

        @Test
        @DisplayName("contractId 为 null 时应校验失败")
        void shouldFailWhenContractIdNull() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            dto.setPaymentCode("PAY-001");
            dto.setContractId(null);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setAmount(new BigDecimal("300000.00"));
            dto.setPaymentDate(LocalDate.of(2026, 4, 15));

            Set<ConstraintViolation<PaymentCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contractId"));
        }

        @Test
        @DisplayName("amount 为 null 时应校验失败")
        void shouldFailWhenAmountNull() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            dto.setPaymentCode("PAY-001");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setAmount(null);
            dto.setPaymentDate(LocalDate.of(2026, 4, 15));

            Set<ConstraintViolation<PaymentCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
        }

        @Test
        @DisplayName("paymentDate 为 null 时应校验失败")
        void shouldFailWhenPaymentDateNull() {
            PaymentCreateDTO dto = new PaymentCreateDTO();
            dto.setPaymentCode("PAY-001");
            dto.setContractId(1L);
            dto.setInitiationId(2L);
            dto.setCustomerId(100L);
            dto.setAmount(new BigDecimal("300000.00"));
            dto.setPaymentDate(null);

            Set<ConstraintViolation<PaymentCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("paymentDate"));
        }
    }
}