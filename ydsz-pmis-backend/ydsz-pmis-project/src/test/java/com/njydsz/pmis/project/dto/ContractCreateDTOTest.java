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
 * ContractCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractCreateDTO 测试")
class ContractCreateDTOTest {

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
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CON-001");
            dto.setContractName("测试合同");
            dto.setInitiationId(1L);
            dto.setCustomerId(100L);
            dto.setCustomerName("测试客户");
            dto.setContractType("FIXED_PRICE");
            dto.setSignDate(LocalDate.of(2026, 1, 1));
            dto.setEffectiveDate(LocalDate.of(2026, 2, 1));
            dto.setExpireDate(LocalDate.of(2027, 2, 1));
            dto.setTotalAmount(new BigDecimal("1000000.00"));
            dto.setCurrency("CNY");
            dto.setPaymentTerms("30%预付，70%验收后付");
            dto.setBillingCycle("MONTHLY");
            dto.setTaxRate(new BigDecimal("0.13"));
            dto.setOwnerId(200L);
            dto.setOwnerName("张三");
            dto.setContractFileId(300L);
            dto.setRemark("测试备注");

            assertThat(dto.getContractCode()).isEqualTo("CON-001");
            assertThat(dto.getContractName()).isEqualTo("测试合同");
            assertThat(dto.getInitiationId()).isEqualTo(1L);
            assertThat(dto.getCustomerId()).isEqualTo(100L);
            assertThat(dto.getCustomerName()).isEqualTo("测试客户");
            assertThat(dto.getContractType()).isEqualTo("FIXED_PRICE");
            assertThat(dto.getSignDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(dto.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1000000.00"));
            assertThat(dto.getCurrency()).isEqualTo("CNY");
            assertThat(dto.getOwnerId()).isEqualTo(200L);
            assertThat(dto.getOwnerName()).isEqualTo("张三");
            assertThat(dto.getContractFileId()).isEqualTo(300L);
            assertThat(dto.getRemark()).isEqualTo("测试备注");
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            ContractCreateDTO dto = new ContractCreateDTO();
            assertThat(dto.getContractCode()).isNull();
            assertThat(dto.getContractName()).isNull();
            assertThat(dto.getInitiationId()).isNull();
            assertThat(dto.getCustomerId()).isNull();
            assertThat(dto.getTotalAmount()).isNull();
            assertThat(dto.getCurrency()).isNull();
        }
    }

    @Nested
    @DisplayName("校验注解")
    class Validation {

        @Test
        @DisplayName("所有必填字段正确填写时应通过校验")
        void shouldPassValidationWhenAllRequiredFieldsPresent() {
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CON-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("1000000.00"));
            dto.setOwnerId(200L);

            Set<ConstraintViolation<ContractCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("contractCode 为空时应校验失败")
        void shouldFailWhenContractCodeBlank() {
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("1000000.00"));
            dto.setOwnerId(200L);

            Set<ConstraintViolation<ContractCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contractCode"));
        }

        @Test
        @DisplayName("contractName 为空时应校验失败")
        void shouldFailWhenContractNameBlank() {
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CON-001");
            dto.setContractName("");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("1000000.00"));
            dto.setOwnerId(200L);

            Set<ConstraintViolation<ContractCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contractName"));
        }

        @Test
        @DisplayName("customerId 为 null 时应校验失败")
        void shouldFailWhenCustomerIdNull() {
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CON-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(null);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("1000000.00"));
            dto.setOwnerId(200L);

            Set<ConstraintViolation<ContractCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("customerId"));
        }

        @Test
        @DisplayName("contractType 为空时应校验失败")
        void shouldFailWhenContractTypeBlank() {
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CON-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("  ");
            dto.setTotalAmount(new BigDecimal("1000000.00"));
            dto.setOwnerId(200L);

            Set<ConstraintViolation<ContractCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contractType"));
        }

        @Test
        @DisplayName("totalAmount 为 null 时应校验失败")
        void shouldFailWhenTotalAmountNull() {
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CON-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(null);
            dto.setOwnerId(200L);

            Set<ConstraintViolation<ContractCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("totalAmount"));
        }

        @Test
        @DisplayName("ownerId 为 null 时应校验失败")
        void shouldFailWhenOwnerIdNull() {
            ContractCreateDTO dto = new ContractCreateDTO();
            dto.setContractCode("CON-001");
            dto.setContractName("测试合同");
            dto.setCustomerId(100L);
            dto.setContractType("FIXED_PRICE");
            dto.setTotalAmount(new BigDecimal("1000000.00"));
            dto.setOwnerId(null);

            Set<ConstraintViolation<ContractCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ownerId"));
        }
    }
}