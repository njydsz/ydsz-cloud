package com.njydsz.pmis.project.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeliveryItemCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DeliveryItemCreateDTO 测试")
class DeliveryItemCreateDTOTest {

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
            DeliveryItemCreateDTO dto = new DeliveryItemCreateDTO();
            dto.setItemCode("DEL-001");
            dto.setInitiationId("1");
            dto.setStandardId("10");
            dto.setProjectType("FIXED_PRICE");
            dto.setProjectLevel("B");
            dto.setDeliveryName("需求规格说明书");
            dto.setDeliveryCategory("DOCUMENT");
            dto.setStage("REQUIREMENTS");
            dto.setRequired(1);
            dto.setPlannedSubmitDate(LocalDate.of(2026, 4, 1));
            dto.setSubmitterId("200");
            dto.setSubmitterName("李四");
            dto.setTrRequired(1);
            dto.setFileIds("file-001,file-002");
            dto.setRemark("测试备注");
            dto.setTenantId("1");

            assertThat(dto.getItemCode()).isEqualTo("DEL-001");
            assertThat(dto.getInitiationId()).isEqualTo("1");
            assertThat(dto.getStandardId()).isEqualTo("10");
            assertThat(dto.getProjectType()).isEqualTo("FIXED_PRICE");
            assertThat(dto.getProjectLevel()).isEqualTo("B");
            assertThat(dto.getDeliveryName()).isEqualTo("需求规格说明书");
            assertThat(dto.getDeliveryCategory()).isEqualTo("DOCUMENT");
            assertThat(dto.getStage()).isEqualTo("REQUIREMENTS");
            assertThat(dto.getRequired()).isEqualTo(1);
            assertThat(dto.getPlannedSubmitDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(dto.getSubmitterId()).isEqualTo("200");
            assertThat(dto.getSubmitterName()).isEqualTo("李四");
            assertThat(dto.getTrRequired()).isEqualTo(1);
            assertThat(dto.getFileIds()).isEqualTo("file-001,file-002");
            assertThat(dto.getRemark()).isEqualTo("测试备注");
            assertThat(dto.getTenantId()).isEqualTo("1");
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            DeliveryItemCreateDTO dto = new DeliveryItemCreateDTO();
            assertThat(dto.getItemCode()).isNull();
            assertThat(dto.getInitiationId()).isNull();
            assertThat(dto.getStandardId()).isNull();
            assertThat(dto.getProjectType()).isNull();
            assertThat(dto.getDeliveryName()).isNull();
        }
    }

    @Nested
    @DisplayName("校验注解")
    class Validation {

        @Test
        @DisplayName("所有必填字段正确填写时应通过校验")
        void shouldPassValidationWhenAllRequiredFieldsPresent() {
            DeliveryItemCreateDTO dto = new DeliveryItemCreateDTO();
            dto.setItemCode("DEL-001");
            dto.setInitiationId("1");

            Set<ConstraintViolation<DeliveryItemCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("itemCode 为空时应校验失败")
        void shouldFailWhenItemCodeBlank() {
            DeliveryItemCreateDTO dto = new DeliveryItemCreateDTO();
            dto.setItemCode("");
            dto.setInitiationId("1");

            Set<ConstraintViolation<DeliveryItemCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("itemCode"));
        }

        @Test
        @DisplayName("initiationId 为 null 时应校验失败")
        void shouldFailWhenInitiationIdNull() {
            DeliveryItemCreateDTO dto = new DeliveryItemCreateDTO();
            dto.setItemCode("DEL-001");
            dto.setInitiationId(null);

            Set<ConstraintViolation<DeliveryItemCreateDTO>> violations = validator.validate(dto);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("initiationId"));
        }
    }
}