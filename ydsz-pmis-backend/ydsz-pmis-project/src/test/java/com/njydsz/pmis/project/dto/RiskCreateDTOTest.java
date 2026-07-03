package com.njydsz.pmis.project.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RiskCreateDTO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RiskCreateDTO 测试")
class RiskCreateDTOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter 赋值应正确")
        void shouldSetAndGetFields() {
            RiskCreateDTO dto = new RiskCreateDTO();
            dto.setRiskCode("RISK-001");
            dto.setInitiationId(1L);
            dto.setRiskTitle("需求变更风险");
            dto.setRiskType("SCOPE");
            dto.setDescription("客户需求频繁变更可能导致项目延期");
            dto.setProbability("HIGH");
            dto.setImpact("MEDIUM");
            dto.setMitigation("加强需求评审流程");
            dto.setContingency("启动备用资源");
            dto.setOwnerId(200L);
            dto.setOwnerName("李四");

            assertThat(dto.getRiskCode()).isEqualTo("RISK-001");
            assertThat(dto.getInitiationId()).isEqualTo(1L);
            assertThat(dto.getRiskTitle()).isEqualTo("需求变更风险");
            assertThat(dto.getRiskType()).isEqualTo("SCOPE");
            assertThat(dto.getDescription()).isEqualTo("客户需求频繁变更可能导致项目延期");
            assertThat(dto.getProbability()).isEqualTo("HIGH");
            assertThat(dto.getImpact()).isEqualTo("MEDIUM");
            assertThat(dto.getMitigation()).isEqualTo("加强需求评审流程");
            assertThat(dto.getContingency()).isEqualTo("启动备用资源");
            assertThat(dto.getOwnerId()).isEqualTo(200L);
            assertThat(dto.getOwnerName()).isEqualTo("李四");
        }

        @Test
        @DisplayName("null 字段赋值后 getter 应返回 null")
        void shouldHandleNullValues() {
            RiskCreateDTO dto = new RiskCreateDTO();
            assertThat(dto.getRiskCode()).isNull();
            assertThat(dto.getInitiationId()).isNull();
            assertThat(dto.getRiskTitle()).isNull();
            assertThat(dto.getRiskType()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getProbability()).isNull();
            assertThat(dto.getImpact()).isNull();
            assertThat(dto.getMitigation()).isNull();
            assertThat(dto.getContingency()).isNull();
            assertThat(dto.getOwnerId()).isNull();
            assertThat(dto.getOwnerName()).isNull();
        }
    }
}