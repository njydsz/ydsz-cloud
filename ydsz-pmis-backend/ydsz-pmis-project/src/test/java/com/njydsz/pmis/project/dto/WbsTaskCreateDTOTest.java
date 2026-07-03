package com.njydsz.pmis.project.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WbsTaskCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("WbsTaskCreateDTO 测试")
class WbsTaskCreateDTOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter 赋值应正确")
        void shouldSetAndGetFields() {
            WbsTaskCreateDTO dto = new WbsTaskCreateDTO();
            dto.setTaskCode("TASK-001");
            dto.setTaskName("需求分析");
            dto.setInitiationId(1L);
            dto.setParentId(null);
            dto.setTaskLevel(1);
            dto.setSortOrder(1);
            dto.setTaskType("TASK");
            dto.setPlannedStartDate(LocalDate.of(2026, 3, 1));
            dto.setPlannedEndDate(LocalDate.of(2026, 3, 15));
            dto.setDurationDays(15);
            dto.setPlannedEffort(new BigDecimal("30.0"));
            dto.setOwnerId(100L);
            dto.setOwnerName("张三");
            dto.setAssigneeIds("100,101");
            dto.setPriority("HIGH");
            dto.setDependsOn(null);
            dto.setMilestone(0);
            dto.setDescription("需求分析任务");
            dto.setDeliverable("需求规格说明书");
            dto.setRiskLevel("LOW");

            assertThat(dto.getTaskCode()).isEqualTo("TASK-001");
            assertThat(dto.getTaskName()).isEqualTo("需求分析");
            assertThat(dto.getInitiationId()).isEqualTo(1