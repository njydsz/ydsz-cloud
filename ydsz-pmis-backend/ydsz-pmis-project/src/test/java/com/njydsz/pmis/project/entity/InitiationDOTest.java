package com.njydsz.pmis.project.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InitiationDO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("InitiationDO 测试")
class InitiationDOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter/getter 赋值应正确")
        void shouldSetAndGetFields() {
            InitiationDO entity = new InitiationDO();
            entity.setId(1L);
            entity.setProjectCode("PRJ-001");
            entity.setProjectName("测试项目");
            entity.setOpportunityId(10L);
            entity.setCustomerId(100L);
            entity.setCustomerName("测试客户");
            entity.setBusinessDeptId(50L);
            entity.setProjectType("FIXED_PRICE");
            entity.setProjectLevel("B");
            entity.setPmId(200L);
            entity.setPmName("李四");
            entity.setSponsorId(300L);
            entity.setSponsorName("王五");
            entity.setEstimatedAmount(new BigDecimal("5000000.00"));
            entity.setBudgetAmount(new BigDecimal("4500000.00"));
            entity.setPlannedStartDate(LocalDate.of(2026, 3, 1));
            entity.setPlannedEndDate(LocalDate.of(2026, 12, 31));
            entity.setDurationDays(306);
            entity.setStage("EXECUTION");
            entity.setCurrentGate("GATE_2");
            entity.setDescription("项目描述");
            entity.setBusinessCase("立项依据");
            entity.setRiskAssessment("风险评估");
            entity.setWorkflowId("WF-001");
            entity.setTenantId(1L);
            entity.setVersion(1);
            entity.setCreatedBy(100L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedBy(200L);
            entity.setUpdatedAt(LocalDateTime.of(2026, 3, 1, 15, 0));
            entity.setDeleted(0);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getProjectCode()).isEqualTo("PRJ-001");
            assertThat(entity.getProjectName()).isEqualTo("测试项目");
            assertThat(entity.getOpportunityId()).isEqualTo(10L);
            assertThat(entity.getCustomerId()).isEqualTo(100L);
            assertThat(entity.getCustomerName()).isEqualTo("测试客户");
            assertThat(entity.getBusinessDeptId()).isEqualTo(50L);
            assertThat(entity.getProjectType()).isEqualTo("FIXED_PRICE");
            assertThat(entity.getProjectLevel()).isEqualTo("B");
            assertThat(entity.getPmId()).isEqualTo(200L);
            assertThat(entity.getPmName()).isEqualTo("李四");
            assertThat(entity.getSponsorId()).isEqualTo(300L);
            assertThat(entity.getSponsorName()).isEqualTo("王五");
            assertThat(entity.getEstimatedAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
            assertThat(entity.getBudgetAmount()).isEqualByComparingTo(new BigDecimal("4500000.00"));
            assertThat(entity.getPlannedStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(entity.getPlannedEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(entity.getDurationDays()).isEqualTo(306);
            assertThat(entity.getStage()).isEqualTo("EXECUTION");
            assertThat(entity.getCurrentGate()).isEqualTo("GATE_2");
            assertThat(entity.getDescription()).isEqualTo("项目描述");
            assertThat(entity.getBusinessCase()).isEqualTo("立项依据");
            assertThat(entity.getRiskAssessment()).isEqualTo("风险评估");
            assertThat(entity.getWorkflowId()).isEqualTo("WF-001");
            assertThat(entity.getTenantId()).isEqualTo(1L);
            assertThat(entity.getVersion()).isEqualTo(1);
            assertThat(entity.getCreatedBy()).isEqualTo(100L);
            assertThat(entity.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
            assertThat(entity.getUpdatedBy()).isEqualTo(200L);
            assertThat(entity.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 15, 0));
            assertThat(entity.getDeleted()).isEqualTo(0);
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            InitiationDO entity = new InitiationDO();
            assertThat(entity.getId()).isNull();
            assertThat(entity.getProjectCode()).isNull();
            assertThat(entity.getProjectName()).isNull();
            assertThat(entity.getOpportunityId()).isNull();
            assertThat(entity.getCustomerId()).isNull();
            assertThat(entity.getEstimatedAmount()).isNull();
            assertThat(entity.getBudgetAmount()).isNull();
            assertThat(entity.getStage()).isNull();
            assertThat(entity.getCurrentGate()).isNull();
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
        @DisplayName("项目金额应正确设置 BigDecimal")
        void shouldSetBigDecimalAmounts() {
            InitiationDO entity = new InitiationDO();
            entity.setEstimatedAmount(new BigDecimal("10000000.00"));
            entity.setBudgetAmount(new BigDecimal("8000000.00"));

            assertThat(entity.getEstimatedAmount()).isEqualByComparingTo(new BigDecimal("10000000.00"));
            assertThat(entity.getBudgetAmount()).isEqualByComparingTo(new BigDecimal("8000000.00"));
        }

        @Test
        @DisplayName("项目日期应正确设置")
        void shouldSetDateFields() {
            InitiationDO entity = new InitiationDO();
            LocalDate start = LocalDate.of(2026, 4, 1);
            LocalDate end = LocalDate.of(2027, 3, 31);

            entity.setPlannedStartDate(start);
            entity.setPlannedEndDate(end);

            assertThat(entity.getPlannedStartDate()).isEqualTo(start);
            assertThat(entity.getPlannedEndDate()).isEqualTo(end);
        }

        @Test
        @DisplayName("乐观锁版本号应正确设置")
        void shouldSetVersionForOptimisticLock() {
            InitiationDO entity = new InitiationDO();
            entity.setVersion(0);
            assertThat(entity.getVersion()).isEqualTo(0);

            entity.setVersion(3);
            assertThat(entity.getVersion()).isEqualTo(3);
        }
    }
}