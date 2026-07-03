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
 * InitiationCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("InitiationCreateDTO 测试")
class InitiationCreateDTOTest {

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
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setOpportunityId(10L);
            dto.setCustomerId(100L);
            dto.setCustomerName("测试客户");
            dto.setBusinessDeptId(50L);
            dto.setProjectType("FIXED_PRICE");
            dto.setProjectLevel("B");
            dto.setPmId(200L);
            dto.setPmName("李四");
            dto.setSponsorId(300L);
            dto.setSponsorName("王五");
            dto.setEstimatedAmount(new BigDecimal("5000000.00"));
            dto.setBudgetAmount(new BigDecimal("4500000.00"));
            dto.setPlannedStartDate(LocalDate.of(2026, 3, 1));
            dto.setPlannedEndDate(LocalDate.of(2026, 12, 31));
            dto.setDescription("项目描述");
            dto.setBusinessCase("立项依据");
            dto.setRiskAssessment("风险评估");

            assertThat(dto.getProjectCode()).isEqualTo("PRJ-001");
            assertThat(dto.getProjectName()).isEqualTo("测试项目");
            assertThat(dto.getOpportunityId()).isEqualTo(10L);
            assertThat(dto.getCustomerId()).isEqualTo(100L);
            assertThat(dto.getCustomerName()).isEqualTo("测试客户");
            assertThat(dto.getBusinessDeptId()).isEqualTo(50L);
            assertThat(dto.getProjectType()).isEqualTo("FIXED_PRICE");
            assertThat(dto.getProjectLevel()).isEqualTo("B");
            assertThat(dto.getPmId()).isEqualTo(200L);
            assertThat(dto.getPmName()).isEqualTo("李四");
            assertThat(dto.getSponsorId()).isEqualTo(300L);
            assertThat(dto.getSponsorName()).isEqualTo("王五");
            assertThat(dto.getEstimatedAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
            assertThat(dto.getBudgetAmount()).isEqualByComparingTo(new BigDecimal("4500000.00"));
            assertThat(dto.getPlannedStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(dto.getPlannedEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(dto.getDescription()).isEqualTo("项目描述");
            assertThat(dto.getBusinessCase()).isEqualTo("立项依据");
            assertThat(dto.getRiskAssessment()).isEqualTo("风险评估");
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            InitiationCreateDTO dto = new InitiationCreateDTO();
            assertThat(dto.getProjectCode()).isNull();
            assertThat(dto.getProjectName()).isNull();
            assertThat(dto.getOpportunityId()).isNull();
            assertThat(dto.getCustomerId()).isNull();
            assertThat(dto.getEstimatedAmount()).isNull();
            assertThat(dto.getBudgetAmount()).isNull();
        }
    }

    @Nested
    @DisplayName("校验注解")
    class Validation {

        @Test
        @DisplayName("所有必填字段正确填写时应通过校验")
        void shouldPassValidationWhenAllRequiredFieldsPresent() {
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("FIXED_PRICE");

            Set<ConstraintViolation<InitiationCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("projectCode 为空时应校验失败")
        void shouldFailWhenProjectCodeBlank() {
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("FIXED_PRICE");

            Set<ConstraintViolation<InitiationCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("projectCode"));
        }

        @Test
        @DisplayName("projectName 为空时应校验失败")
        void shouldFailWhenProjectNameBlank() {
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("");
            dto.setCustomerId(100L);
            dto.setProjectType("FIXED_PRICE");

            Set<ConstraintViolation<InitiationCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("projectName"));
        }

        @Test
        @DisplayName("customerId 为 null 时应校验失败")
        void shouldFailWhenCustomerIdNull() {
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setCustomerId(null);
            dto.setProjectType("FIXED_PRICE");

            Set<ConstraintViolation<InitiationCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("customerId"));
        }

        @Test
        @DisplayName("projectType 为空时应校验失败")
        void shouldFailWhenProjectTypeBlank() {
            InitiationCreateDTO dto = new InitiationCreateDTO();
            dto.setProjectCode("PRJ-001");
            dto.setProjectName("测试项目");
            dto.setCustomerId(100L);
            dto.setProjectType("  ");

            Set<ConstraintViolation<InitiationCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("projectType"));
        }
    }
}