package com.njydsz.pmis.project.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExpenseCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ExpenseCreateDTO 测试")
class ExpenseCreateDTOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter 赋值应正确")
        void shouldSetAndGetFields() {
            ExpenseCreateDTO dto = new ExpenseCreateDTO();
            dto.setExpenseCode("EXP-001");
            dto.setInitiationId(1L);
            dto.setEmployeeId(100L);
            dto.setEmployeeName("张三");
            dto.setExpenseType("TRAVEL");
            dto.setAmount(new BigDecimal("5000.00"));
            dto.setExpenseDate(LocalDate.of(2026, 3, 15));
            dto.setDescription("差旅费报销");
            dto.setReceiptUrl("http://example.com/receipt.jpg");

            assertThat(dto.getExpenseCode()).isEqualTo("EXP-001");
            assertThat(dto.getInitiationId()).isEqualTo(1L);
            assertThat(dto.getEmployeeId()).isEqualTo(100L);
            assertThat(dto.getEmployeeName()).isEqualTo("张三");
            assertThat(dto.getExpenseType()).isEqualTo("TRAVEL");
            assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
            assertThat(dto.getExpenseDate()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(dto.getDescription()).isEqualTo("差旅费报销");
            assertThat(dto.getReceiptUrl()).isEqualTo("http://example.com/receipt.jpg");
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            ExpenseCreateDTO dto = new ExpenseCreateDTO();
            assertThat(dto.getExpenseCode()).isNull();
            assertThat(dto.getInitiationId()).isNull();
            assertThat(dto.getEmployeeId()).isNull();
            assertThat(dto.getEmployeeName()).isNull();
            assertThat(dto.getExpenseType()).isNull();
            assertThat(dto.getAmount()).isNull();
            assertThat(dto.getExpenseDate()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getReceiptUrl()).isNull();
        }
    }
}