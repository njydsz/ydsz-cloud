package com.njydsz.pmis.common.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AuditInfo 值对象单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AuditInfo 值对象测试")
class AuditInfoTest {

    @Test
    @DisplayName("相同属性值的 AuditInfo 应相等")
    void shouldEqualWhenSameValues() {
        AuditInfo info1 = new AuditInfo("user1", null, "user1", null);
        AuditInfo info2 = new AuditInfo("user1", null, "user1", null);
        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    @DisplayName("不同属性值的 AuditInfo 不应相等")
    void shouldNotEqualWhenDifferentValues() {
        AuditInfo info1 = new AuditInfo("user1", null, "user1", null);
        AuditInfo info2 = new AuditInfo("user2", null, "user2", null);
        assertNotEquals(info1, info2);
    }

    @Test
    @DisplayName("withUpdate 应返回新实例且保留 createdBy")
    void shouldReturnNewInstanceOnWithUpdate() {
        AuditInfo original = AuditInfo.of("user1", null);
        AuditInfo updated = original.withUpdate("user2", null);
        assertEquals("user1", updated.getCreatedBy());
        assertEquals("user2", updated.getUpdatedBy());
        assertNotEquals(original, updated);
    }

    @Test
    @DisplayName("isFresh 在 createdAt 为 null 时返回 true")
    void shouldReturnTrueForIsFreshWhenNoCreatedAt() {
        AuditInfo info = AuditInfo.empty();
        assertTrue(info.isFresh());
    }

    @Test
    @DisplayName("from(Auditable) 应正确提取审计信息")
    void shouldExtractFromAuditable() {
        Auditable auditable = new TestAuditable("creator", "updater");
        AuditInfo info = AuditInfo.from(auditable);
        assertEquals("creator", info.getCreatedBy());
        assertEquals("updater", info.getUpdatedBy());
    }

    @Test
    @DisplayName("from(null) 应返回空的 AuditInfo")
    void shouldReturnEmptyWhenFromNull() {
        AuditInfo info = AuditInfo.from(null);
        assertTrue(info.isFresh());
    }

    private static class TestAuditable implements Auditable {
        private final String createdBy;
        private final String updatedBy;

        TestAuditable(String createdBy, String updatedBy) {
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
        }

        @Override
        public String getCreatedBy() { return createdBy; }
        @Override
        public void setCreatedBy(String createdBy) { }
        @Override
        public LocalDateTime getCreatedAt() { return null; }
        @Override
        public void setCreatedAt(LocalDateTime createdAt) { }
        @Override
        public String getUpdatedBy() { return updatedBy; }
        @Override
        public void setUpdatedBy(String updatedBy) { }
        @Override
        public LocalDateTime getUpdatedAt() { return null; }
        @Override
        public void setUpdatedAt(LocalDateTime updatedAt) { }
    }
}
