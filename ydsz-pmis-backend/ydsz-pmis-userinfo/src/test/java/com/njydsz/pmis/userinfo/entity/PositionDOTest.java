package com.njydsz.pmis.userinfo.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PositionDO 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PositionDO 测试")
class PositionDOTest {

    @Nested
    @DisplayName("构造与字段赋值")
    class ConstructionAndFieldAssignment {

        @Test
        @DisplayName("默认构造 + setter/getter 赋值应正确")
        void shouldSetAndGetFields() {
            PositionDO entity = new PositionDO();
            entity.setId(1L);
            entity.setPositionCode("POS-DEV-001");
            entity.setPositionName("Java开发工程师");
            entity.setDepartmentId(10L);
            entity.setLevelCode("L5");
            entity.setDescription("负责后端开发");
            entity.setStatus("ENABLED");
            entity.setTenantId(1L);
            entity.setCreatedBy(100L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedBy(200L);
            entity.setUpdatedAt(LocalDateTime.of(2026, 3, 1, 15, 0));
            entity.setDeleted(0);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getPositionCode()).isEqualTo("POS-DEV-001");
            assertThat(entity.getPositionName()).isEqualTo("Java开发工程师");
            assertThat(entity.getDepartmentId()).isEqualTo(10L);
            assertThat(entity.getLevelCode()).isEqualTo("L5");
            assertThat(entity.getDescription()).isEqualTo("负责后端开发");
            assertThat(entity.getStatus()).isEqualTo("ENABLED");
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
            PositionDO entity = new PositionDO();
            assertThat(entity.getId()).isNull();
            assertThat(entity.getPositionCode()).isNull();
            assertThat(entity.getPositionName()).isNull();
            assertThat(entity.getDepartmentId()).isNull();
            assertThat(entity.getLevelCode()).isNull();
            assertThat(entity.getDescription()).isNull();
            assertThat(entity.getStatus()).isNull();
            assertThat(entity.getTenantId()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
            assertThat(entity.getDeleted()).isNull();
        }
    }

    @Nested
    @DisplayName("业务字段")
    class BusinessFields {

        @Test
        @DisplayName("岗位状态 ENABLED/DISABLED 应正确设置")
        void shouldSetStatusEnum() {
            PositionDO entity = new PositionDO();
            entity.setStatus("ENABLED");
            assertThat(entity.getStatus()).isEqualTo("ENABLED");

            entity.setStatus("DISABLED");
            assertThat(entity.getStatus()).isEqualTo("DISABLED");
        }

        @Test
        @DisplayName("逻辑删除标志 0/1 应正确设置")
        void shouldSetDeletedFlag() {
            PositionDO entity = new PositionDO();
            entity.setDeleted(0);
            assertThat(entity.getDeleted()).isEqualTo(0);

            entity.setDeleted(1);
            assertThat(entity.getDeleted()).isEqualTo(1);
        }
    }
}
