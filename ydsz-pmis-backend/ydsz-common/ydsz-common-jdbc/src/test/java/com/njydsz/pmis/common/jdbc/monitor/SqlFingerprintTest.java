package com.njydsz.common.jdbc.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SQL 指纹归一化工具测试
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@DisplayName("SqlFingerprint - SQL 指纹归一化测试")
class SqlFingerprintTest {

    @Test
    @DisplayName("字符串字面量应被替换为 ?")
    void shouldReplaceStringLiteral() {
        String sql = "SELECT * FROM users WHERE name = 'alice' AND email = 'bob@test.com'";
        String fingerprint = SqlFingerprint.fingerprint(sql);
        assertEquals("SELECT * FROM users WHERE name = ? AND email = ?", fingerprint);
    }

    @Test
    @DisplayName("数字字面量应被替换为 ?")
    void shouldReplaceNumberLiteral() {
        String sql = "SELECT * FROM orders WHERE id = 123 AND amount = 45.67";
        String fingerprint = SqlFingerprint.fingerprint(sql);
        assertEquals("SELECT * FROM orders WHERE id = ? AND amount = ?", fingerprint);
    }

    @Test
    @DisplayName("IN 列表应被折叠为 IN (?)")
    void shouldCollapseInList() {
        String sql = "SELECT * FROM orders WHERE id IN (1, 2, 3, 4, 5)";
        String fingerprint = SqlFingerprint.fingerprint(sql);
        assertEquals("SELECT * FROM orders WHERE id IN (?)", fingerprint);
    }

    @Test
    @DisplayName("多空格应被折叠为单空格")
    void shouldCollapseMultiSpace() {
        String sql = "SELECT   *   FROM    users";
        String fingerprint = SqlFingerprint.fingerprint(sql);
        assertEquals("SELECT * FROM users", fingerprint);
    }

    @Test
    @DisplayName("超长 SQL 应被截断")
    void shouldTruncateLongSql() {
        StringBuilder sb = new StringBuilder("SELECT * FROM table WHERE ");
        for (int i = 0; i < 100; i++) {
            sb.append("col_").append(i).append(" = ").append(i).append(" AND ");
        }
        sb.append("1 = 1");
        String fingerprint = SqlFingerprint.fingerprint(sb.toString());
        assertNotNull(fingerprint);
        assertEquals(203, fingerprint.length()); // 200 + "..."
        assertEquals("...", fingerprint.substring(200));
    }

    @Test
    @DisplayName("null 输入应返回 unknown")
    void shouldReturnUnknownForNull() {
        assertEquals("unknown", SqlFingerprint.fingerprint(null));
    }

    @Test
    @DisplayName("空字符串应返回 unknown")
    void shouldReturnUnknownForEmpty() {
        assertEquals("unknown", SqlFingerprint.fingerprint(""));
    }

    @Test
    @DisplayName("复合 SQL 应正确归一化")
    void shouldNormalizeComplexSql() {
        String sql = "SELECT u.name, o.amount FROM users u JOIN orders o ON u.id = o.user_id WHERE u.id = 1 AND o.status IN ('paid', 'pending', 'shipped')";
        String fingerprint = SqlFingerprint.fingerprint(sql);
        assertEquals("SELECT u.name, o.amount FROM users u JOIN orders o ON u.id = ? AND o.status IN (?)", fingerprint);
    }
}
