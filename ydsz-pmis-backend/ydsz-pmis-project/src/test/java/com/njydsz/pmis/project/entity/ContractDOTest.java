package com.njydsz.pmis.project.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContractDO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractDO 测试")
class ContractDOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter/getter 赋值应正确")
        void shouldSetAndGetFields() {
            ContractDO entity = new ContractDO();
            entity.setId(1L);
            entity.setContractCode("CON-001");
            entity.setContractName("测试合同");
            entity.setInitiationId(10L);
            entity.setCustomerId(100L);
            entity.setCustomerName("测试客户");
            entity.setContractType("FIXED_PRICE");
            entity.setSignDate(LocalDate.of(2026, 1, 1));
            entity.setEffectiveDate(LocalDate.of(2026, 2, 1));
            entity.setExpireDate(LocalDate.of(2027, 2, 1));
            entity.setTotalAmount(new BigDecimal("1000000.00"));
            entity.setCurrency("CNY");
            entity.setPaymentTerms("30%预付");
            entity.setBillingCycle("MONTHLY");
            entity.setTaxRate(new BigDecimal("0.13"));
            entity.setStatus("ACTIVE");
            entity.setRiskLevel("LOW");
            entity.setRiskNotes("风险说明");
            entity.setOwnerId(200L);
            entity.setOwnerName("张三");
            entity.setContractFileId(300L);
            entity.setWorkflowId("WF-001");
            entity.setRemark("测试备注");
            entity.setTenantId(1L);
            entity.setVersion(1);
            entity.setCreatedBy(100L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedBy(200L);
            entity.setUpdatedAt(LocalDateTime.of(2026, 3, 1, 15, 0));
            entity.setDeleted(0);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getContractCode()).isEqualTo("CON-001");
            assertThat(entity.getContractName()).isEqualTo("测试合同");
            assertThat(entity.getInitiationId()).isEqualTo(10L);
            assertThat(entity.getCustomerId()).isEqualTo(100L);
            assertThat(entity.getCustomerName()).isEqualTo("测试客户");
            assertThat(entity.getContractType()).isEqualTo("FIXED_PRICE");
            assertThat(entity.getSignDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(entity.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(entity.getExpireDate()).isEqualTo(LocalDate.of(2027, 2, 1));
            assertThat(entity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1000000.00"));
            assertThat(entity.getCurrency()).isEqualTo("CNY");
            assertThat(entity.getPaymentTerms()).isEqualTo("30%预付");
            assertThat(entity.getBillingCycle()).isEqualTo("MONTHLY");
            assertThat(entity.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.13"));
            assertThat(entity.getStatus()).isEqualTo("ACTIVE");
            assertThat(entity.getRiskLevel()).isEqualTo("LOW");
            assertThat(entity.getRiskNotes()).isEqualTo("风险说明");
            assertThat(entity.getOwnerId()).isEqualTo(200L);
            assertThat(entity.getOwnerName()).isEqualTo("张三");
            assertThat(entity.getContractFileId()).isEqualTo(300L);
            assertThat(entity.getWorkflowId()).isEqualTo("WF-001");
            assertThat(entity.getRemark()).isEqualTo("测试备注");
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
            ContractDO entity = new ContractDO();
            assertThat(entity.getId()).isNull();
            assertThat(entity.getContractCode()).isNull();
            assertThat(entity.getContractName()).isNull();
            assertThat(entity.getInitiationId()).isNull();
            assertThat(entity.getCustomerId()).isNull();
            assertThat(entity.getTotalAmount()).isNull();
            assertThat(entity.getCurrency()).isNull();
            assertThat(entity.getStatus()).isNull();
            assertThat(entity.getRiskLevel()).isNull();
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
        @DisplayName("合同金额应正确设置 BigDecimal")
        void shouldSetBigDecimalAmount() {
            ContractDO entity = new ContractDO();
            entity.setTotalAmount(new BigDecimal("9999999.99"));
            assertThat(entity.getTotalAmount()).isEqualByComparingTo(new BigDecimal("9999999.99"));
        }

        @Test
        @DisplayName("合同日期字段应正确设置 LocalDate")
        void shouldSetLocalDateFields() {
            ContractDO entity = new ContractDO();
            LocalDate signDate = LocalDate.of(2026, 6, 15);
            LocalDate effectiveDate = LocalDate.of(2026, 7, 1);
            LocalDate expireDate = LocalDate.of(2027, 6, 30);

            entity.setSignDate(signDate);
            entity.setEffectiveDate(effectiveDate);
            entity.setExpireDate(expireDate);

            assertThat(entity.getSignDate()).isEqualTo(signDate);
            assertThat(entity.getEffectiveDate()).isEqualTo(effectiveDate);
            assertThat(entity.getExpireDate()).isEqualTo(expireDate);
        }

        @Test
        @DisplayName("乐观锁版本号应正确设置")
        void shouldSetVersionForOptimisticLock() {
            ContractDO entity = new ContractDO();
            entity.setVersion(0);
            assertThat(entity.getVersion()).isEqualTo(0);

            entity.setVersion(5);
            assertThat(entity.getVersion()).isEqualTo(5);
        }
    }
}