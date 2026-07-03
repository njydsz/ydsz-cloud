package com.njydsz.pmis.project.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TimeEntryCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TimeEntryCreateDTO 测试")
class TimeEntryCreateDTOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter 赋值应正确")
        void shouldSetAndGetFields() {
            TimeEntryCreateDTO dto = new TimeEntryCreateDTO();
            dto.setEntryDate(LocalDate.of(2026, 3, 10));
            dto.setEmployeeId(100L);
            dto.setEmployeeName("张三");
            dto.setLevelCode("L5");
            dto.setInitiationId(1L);
            dto.setInitiationName("测试项目");
            dto.setTaskId(200L);
            dto.setTaskName("需求分析");
            dto.setHours(new BigDecimal("8.0"));
            dto.setOvertime(new BigDecimal("2.0"));
            dto.setWorkType("DEVELOPMENT");
            dto.setDescription("完成需求分析文档");

            assertThat(dto.getEntryDate()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(dto.getEmployeeId()).isEqualTo(100L);
            assertThat(dto.getEmployeeName()).isEqualTo("张三");
            assertThat(dto.getLevelCode()).isEqualTo("L5");
            assertThat(dto.getInitiationId()).isEqualTo(1L);
            assertThat(dto.getInitiationName()).isEqualTo("测试项目");
            assertThat(dto.getTaskId()).isEqualTo(200L);
            assertThat(dto.getTaskName()).isEqualTo("需求分析");
            assertThat(dto.getHours()).isEqualByComparingTo(new BigDecimal("8.0"));
            assertThat(dto.getOvertime()).isEqualByComparingTo(new BigDecimal("2.0"));
            assertThat(dto.getWorkType()).isEqualTo("DEVELOPMENT");
            assertThat(dto.getDescription()).isEqualTo("完成需求分析文档");
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            TimeEntryCreateDTO dto = new TimeEntryCreateDTO();
            assertThat(dto.getEntryDate()).isNull();
            assertThat(dto.getEmployeeId()).isNull();
            assertThat(dto.getEmployeeName()).isNull();
            assertThat(dto.getLevelCode()).isNull();
            assertThat(dto.getInitiationId()).isNull();
            assertThat(dto.getInitiationName()).isNull();
            assertThat(dto.getTaskId()).isNull();
            assertThat(dto.getTaskName()).isNull();
            assertThat(dto.getHours()).isNull();
            assertThat(dto.getOvertime()).isNull();
            assertThat(dto.getWorkType()).isNull();
            assertThat(dto.getDescription()).isNull();
        }
    }
}