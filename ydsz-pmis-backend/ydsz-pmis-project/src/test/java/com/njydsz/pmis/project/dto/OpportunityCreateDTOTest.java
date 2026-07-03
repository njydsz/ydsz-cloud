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
 * OpportunityCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("OpportunityCreateDTO 测试")
class OpportunityCreateDTOTest {

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
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setCustomerName("测试客户");
            dto.setBusinessDeptId(50L);
            dto.setOwnerId(200L);
            dto.setOwnerName("张三");
            dto.setLevel("A");
            dto.setSource("官网");
            dto.setIndustry("金融");
            dto.setEstimatedAmount(new BigDecimal("3000000.00"));
            dto.setWinRate(new BigDecimal("0.6"));
            dto.setExpectedSignDate(LocalDate.of(2026, 6, 30));
            dto.setExpectedStartDate(LocalDate.of(2026, 7, 1));
            dto.setExpectedEndDate(LocalDate.of(2027, 6, 30));
            dto.setCompetitor("竞争对手A");
            dto.setRemark("测试备注");
            dto.setTags("tag1,tag2");

            assertThat(dto.getOpportunityCode()).isEqualTo("OPP-001");
            assertThat(dto.getOpportunityName()).isEqualTo("测试商机");
            assertThat(dto.getCustomerId()).isEqualTo(100L);
            assertThat(dto.getCustomerName()).isEqualTo("测试客户");
            assertThat(dto.getBusinessDeptId()).isEqualTo(50L);
            assertThat(dto.getOwnerId()).isEqualTo(200L);
            assertThat(dto.getOwnerName()).isEqualTo("张三");
            assertThat(dto.getLevel()).isEqualTo("A");
            assertThat(dto.getSource()).isEqualTo("官网");
            assertThat(dto.getIndustry()).isEqualTo("金融");
            assertThat(dto.getEstimatedAmount()).isEqualByComparingTo(new BigDecimal("3000000.00"));
            assertThat(dto.getWinRate()).isEqualByComparingTo(new BigDecimal("0.6"));
            assertThat(dto.getExpectedSignDate()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(dto.getExpectedStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(dto.getExpectedEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
            assertThat(dto.getCompetitor()).isEqualTo("竞争对手A");
            assertThat(dto.getRemark()).isEqualTo("测试备注");
            assertThat(dto.getTags()).isEqualTo("tag1,tag2");
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            assertThat(dto.getOpportunityCode()).isNull();
            assertThat(dto.getOpportunityName()).isNull();
            assertThat(dto.getCustomerId()).isNull();
            assertThat(dto.getOwnerId()).isNull();
            assertThat(dto.getEstimatedAmount()).isNull();
            assertThat(dto.getWinRate()).isNull();
        }
    }

    @Nested
    @DisplayName("校验注解")
    class Validation {

        @Test
        @DisplayName("所有必填字段正确填写时应通过校验")
        void shouldPassValidationWhenAllRequiredFieldsPresent() {
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(200L);

            Set<ConstraintViolation<OpportunityCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("opportunityCode 为空时应校验失败")
        void shouldFailWhenOpportunityCodeBlank() {
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(200L);

            Set<ConstraintViolation<OpportunityCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("opportunityCode"));
        }

        @Test
        @DisplayName("opportunityName 为空时应校验失败")
        void shouldFailWhenOpportunityNameBlank() {
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("");
            dto.setCustomerId(100L);
            dto.setOwnerId(200L);

            Set<ConstraintViolation<OpportunityCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("opportunityName"));
        }

        @Test
        @DisplayName("customerId 为 null 时应校验失败")
        void shouldFailWhenCustomerIdNull() {
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(null);
            dto.setOwnerId(200L);

            Set<ConstraintViolation<OpportunityCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("customerId"));
        }

        @Test
        @DisplayName("ownerId 为 null 时应校验失败")
        void shouldFailWhenOwnerIdNull() {
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("OPP-001");
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(null);

            Set<ConstraintViolation<OpportunityCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ownerId"));
        }

        @Test
        @DisplayName("opportunityCode 超过 64 字符时应校验失败")
        void shouldFailWhenOpportunityCodeExceedsMaxSize() {
            OpportunityCreateDTO dto = new OpportunityCreateDTO();
            dto.setOpportunityCode("A".repeat(65));
            dto.setOpportunityName("测试商机");
            dto.setCustomerId(100L);
            dto.setOwnerId(200L);

            Set<ConstraintViolation<OpportunityCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("opportunityCode"));
        }
    }
}