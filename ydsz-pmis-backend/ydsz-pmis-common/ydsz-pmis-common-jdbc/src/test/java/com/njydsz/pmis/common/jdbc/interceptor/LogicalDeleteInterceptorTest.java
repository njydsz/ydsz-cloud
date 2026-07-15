package com.njydsz.pmis.common.jdbc.interceptor;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 逻辑删除拦截器测试
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@DisplayName("LogicalDeleteInterceptor - 逻辑删除拦截器测试")
class LogicalDeleteInterceptorTest {

    private LogicalDeleteInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new LogicalDeleteInterceptor();
        interceptor.setDeletedColumn("deleted");
        interceptor.setDeletedValue(1L);
        interceptor.setNormalValue(0L);
    }

    @Test
    @DisplayName("DELETE 语句应被转换为 UPDATE SET deleted=1")
    void shouldConvertDeleteToUpdate() throws JSQLParserException {
        Delete delete = (Delete) CCJSqlParserUtil.parse("DELETE FROM rs_company WHERE id = 1");
        String result = interceptor.convertDeleteToLogicalUpdateSql(delete);
        assertTrue(result.contains("UPDATE"));
        assertTrue(result.contains("deleted = 1"));
        assertTrue(result.contains("deleted = 0"));
        assertTrue(result.contains("id = 1"));
    }

    @Test
    @DisplayName("无 WHERE 的 DELETE 应添加 deleted=0 条件")
    void shouldAddDeletedConditionToDeleteWithoutWhere() throws JSQLParserException {
        Delete delete = (Delete) CCJSqlParserUtil.parse("DELETE FROM rs_company");
        String result = interceptor.convertDeleteToLogicalUpdateSql(delete);
        assertTrue(result.contains("WHERE"));
        assertTrue(result.contains("deleted = 0"));
    }

    @Test
    @DisplayName("忽略表不应被转换，返回原始 DELETE SQL")
    void shouldNotConvertIgnoredTable() throws JSQLParserException {
        interceptor.setIgnoreTables(Set.of("sys_config"));
        Delete delete = (Delete) CCJSqlParserUtil.parse("DELETE FROM sys_config WHERE id = 1");
        String result = interceptor.convertDeleteToLogicalUpdateSql(delete);
        assertTrue(result.contains("DELETE"));
        assertFalse(result.contains("UPDATE"));
    }

    @Test
    @DisplayName("多表 DELETE 应抛出异常")
    void shouldThrowOnMultiTableDelete() throws JSQLParserException {
        Delete delete = (Delete) CCJSqlParserUtil.parse("DELETE FROM rs_company WHERE id = 1");
        // 模拟多表删除场景
        delete.setTables(java.util.List.of(new net.sf.jsqlparser.schema.Table("t1")));
        assertThrows(IllegalStateException.class,
                () -> interceptor.convertDeleteToLogicalUpdateSql(delete));
    }

    @Test
    @DisplayName("null Delete 参数应抛出 IllegalArgumentException")
    void shouldThrowOnNullDelete() {
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.convertDeleteToLogicalUpdateSql(null));
    }

    @Test
    @DisplayName("SELECT 语句应追加 deleted=0 条件到 WHERE")
    void shouldAddDeletedConditionToSelect() throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse("SELECT * FROM rs_company WHERE name = 'test'");
        PlainSelect plainSelect = (PlainSelect) select;
        interceptor.processPlainSelect(plainSelect);
        String result = plainSelect.toString();
        assertTrue(result.contains("deleted = 0"));
        assertTrue(result.contains("name = 'test'"));
    }

    @Test
    @DisplayName("无 WHERE 的 SELECT 应添加 deleted=0 条件")
    void shouldAddDeletedConditionToSelectWithoutWhere() throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse("SELECT * FROM rs_company");
        PlainSelect plainSelect = (PlainSelect) select;
        interceptor.processPlainSelect(plainSelect);
        String result = plainSelect.toString();
        assertTrue(result.contains("WHERE"));
        assertTrue(result.contains("deleted = 0"));
    }

    @Test
    @DisplayName("忽略表的 SELECT 不应追加 deleted 条件")
    void shouldNotAddConditionForIgnoredTable() throws JSQLParserException {
        interceptor.setIgnoreTables(Set.of("sys_config"));
        Select select = (Select) CCJSqlParserUtil.parse("SELECT * FROM sys_config WHERE id = 1");
        PlainSelect plainSelect = (PlainSelect) select;
        interceptor.processPlainSelect(plainSelect);
        String result = plainSelect.toString();
        assertFalse(result.contains("deleted"));
    }

    @Test
    @DisplayName("JOIN 查询应在 ON 条件中追加 alias.deleted=0")
    void shouldAddDeletedConditionToJoinOn() throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse(
                "SELECT * FROM orders o JOIN users u ON o.user_id = u.id WHERE o.status = 'active'");
        PlainSelect plainSelect = (PlainSelect) select;
        interceptor.processPlainSelect(plainSelect);
        String result = plainSelect.toString();
        assertTrue(result.contains("deleted = 0"));
        assertTrue(result.contains("o.status = 'active'"));
    }

    @Test
    @DisplayName("子查询 FROM 应递归处理")
    void shouldRecursivelyProcessSubqueryFrom() throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse(
                "SELECT * FROM (SELECT * FROM rs_company) AS sub WHERE sub.name = 'test'");
        PlainSelect plainSelect = (PlainSelect) select;
        interceptor.processPlainSelect(plainSelect);
        String result = plainSelect.toString();
        assertTrue(result.contains("deleted = 0"));
    }

    @Test
    @DisplayName("ignoreTables 大小写不敏感")
    void shouldBeCaseInsensitiveForIgnoreTables() {
        interceptor.setIgnoreTables(Set.of("SYS_CONFIG"));
        assertTrue(interceptor.getIgnoreTables().contains("sys_config"));
        assertFalse(interceptor.getIgnoreTables().contains("SYS_CONFIG"));
    }
}
