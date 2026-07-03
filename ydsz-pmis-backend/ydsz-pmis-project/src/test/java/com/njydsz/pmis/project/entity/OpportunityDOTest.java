package com.njydsz.pmis.project.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpportunityDO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("OpportunityDO 测试")
class OpportunityDOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter/getter 赋值应正确")
        void shouldSetAndGetFields() {
            OpportunityDO entity = new OpportunityDO();
            entity.setId(1L);
            entity.setOpportunityCode("OPP-001");
            entity.setOpportunityName("测试商机");
            entity.setCustomerId(100L);
            entity.setCustomerName("测试客户");
            entity.setBusinessDeptId(50L);
            entity.setOwnerId(200L);
            entity.setOwnerName("张三");
            entity.setLevel("A");
            entity.setSource("官网");
            entity.setIndustry("金融");
            entity.setEstimatedAmount(new BigDecimal("3000000.00"));
            entity.setWinRate(new BigDecimal("0.6"));
            entity.setExpectedSignDate(LocalDate.of(2026, 6, 30));
            entity.setExpectedStartDate(LocalDate.of(2026, 7, 1));
            entity.setExpectedEndDate(LocalDate.of(2027, 6, 30));
            entity.setStatus("ACTIVE");
            entity.setLostReason(null);
            entity.setCompetitor("竞争对手X");
            entity.setRemark("备注");
            entity.setTags("tag1,tag2");
            entity.setTenantId(1L);
            entity.setCreatedBy(100L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedBy(200L);
            entity.setUpdatedAt(LocalDateTime.of(2026, 3, 1, 15, 0));
            entity.setDeleted(0);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getOpportunityCode()).isEqualTo("OPP-001");
            assertThat(entity.getOpportunityName()).isEqualTo("测试商机");
            assertThat(entity.getCustomerId()).isEqualTo(100L);
            assertThat(entity.getCustomerName()).isEqualTo("测试客户");
            assertThat(entity.getBusinessDeptId()).isEqualTo(50L);
            assertThat(entity.getOwnerId()).isEqualTo(200L);
            assertThat(entity.getOwnerName()).isEqualTo("张三");
            assertThat(entity.getLevel()).isEqualTo("A");
            assertThat(entity.getSource()).isEqualTo("官网");
            assertThat(entity.getIndustry()).isEqualTo("金融");
            assertThat(entity.getEstimatedAmount()).isEqualByComparingTo(new BigDecimal("3000000.00"));
            assertThat(entity.getWinRate()).isEqualByComparingTo(new BigDecimal("0.6"));
            assertThat(entity.getExpectedSignDate()).isEqualTo(LocalDate.of(2026, 6, 30));
            assertThat(entity.getExpectedStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(entity.getExpectedEndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
            assertThat(entity.getStatus()).isEqualTo("ACTIVE");
            assertThat(entity.getLostReason()).isNull();
            assertThat(entity.getCompetitor()).isEqualTo("竞争对手X");
            assertThat(entity.getRemark()).isEqualTo("备注");
            assertThat(entity.getTags()).isEqualTo("tag1,tag2");
            assertThat(entity.getTenantId()).isEqualTo(1L);
            assertThat(entity.getCreatedBy()).isEqualTo(100L);
            assertThat(entity.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
            assertThat(entity.getUpdatedBy()).isEqualTo(200L);
            assertThat(entity.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 15, 0));
            assertThat(entity.getDeleted()).isEqualTo(0);
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            OpportunityDO entity = new OpportunityDO();
            assertThat(entity.getId()).isNull();
            assertThat(entity.getOpportunityCode()).isNull();
            assertThat(entity.getOpportunityName()).isNull();
            assertThat(entity.getCustomerId()).isNull();
            assertThat(entity.getOwnerId()).isNull();
            assertThat(entity.getLevel()).isNull();
            assertThat(entity.getEstimatedAmount()).isNull();
            assertThat(entity.getWinRate()).isNull();
            assertThat(entity.getStatus()).isNull();
            assertThat(entity.getLostReason()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
            assertThat(entity.getDeleted()).isNull();
        }
    }

    @Nested
    @DisplayName("业务字段")
    class BusinessFields {

        @Test
        @DisplayName("商机金额和赢率应正确设置")
        void shouldSetAmountAndWinRate() {
            OpportunityDO entity = new OpportunityDO();
            entity.setEstimatedAmount(new BigDecimal("5000000.00"));
            entity.setWinRate(new BigDecimal("0.75"));

            assertThat(entity.getEstimatedAmount()).isEqualByComparingTo(new BigDecimal("5000000.00"));
            assertThat(entity.getWinRate()).isEqualByComparingTo(new BigDecimal("0.75"));
        }

        @Test
        @DisplayName("输单原因可正确设置和读取")
        void shouldSetLostReason() {
            OpportunityDO entity = new OpportunityDO();
            entity.setLostReason("价格过高");
            assertThat(entity.getLostReason()).isEqualTo("价格过高");
        }

        @Test
        @DisplayName("商机状态应正确设置")
        void shouldSetStatus() {
            OpportunityDO entity = new OpportunityDO();
            entity.setStatus("WON");
            assertThat(entity.getStatus()).isEqualTo("WON");

            entity.setStatus("LOST");
            assertThat(entity.getStatus()).isEqualTo("LOST");
        }
    }
}