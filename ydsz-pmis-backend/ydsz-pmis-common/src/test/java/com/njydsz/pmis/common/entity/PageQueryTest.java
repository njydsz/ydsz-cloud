package com.njydsz.pmis.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PageQuery} 分页查询参数测试
 *
 * <p>覆盖默认值、offset 计算、排序字段白名单校验、排序方向安全处理等核心逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PageQuery 分页查询参数测试")
class PageQueryTest {

    @Nested
    @DisplayName("默认值与基本属性")
    class DefaultValueTest {

        @Test
        @DisplayName("新实例应使用默认 page=1、size=10、orderDir=desc")
        void shouldUseDefaultValues() {
            PageQuery query = new PageQuery();
            assertThat(query.getPage()).isEqualTo(1L);
            assertThat(query.getSize()).isEqualTo(10L);
            assertThat(query.getOrderDir()).isEqualTo("desc");
            assertThat(query.getOrderBy()).isNull();
            assertThat(query.getKeyword()).isNull();
        }

        @Test
        @DisplayName("MAX_SIZE 常量应为 200")
        void shouldHaveMaxSizeConstant() {
            assertThat(PageQuery.MAX_SIZE).isEqualTo(200L);
        }

        @Test
        @DisplayName("ORDER_BY_PATTERN 仅允许字母/数字/下划线且以字母开头")
        void shouldHaveOrderByPattern() {
            assertThat(PageQuery.ORDER_BY_PATTERN).isEqualTo("^[a-zA-Z][a-zA-Z0-9_]*$");
        }
    }

    @Nested
    @DisplayName("offset() 偏移量计算")
    class OffsetTest {

        @Test
        @DisplayName("第 1 页 size=10 偏移量为 0")
        void shouldReturnZeroForFirstPage() {
            PageQuery query = new PageQuery();
            assertThat(query.offset()).isEqualTo(0L);
        }

        @Test
        @DisplayName("第 3 页 size=20 偏移量为 40")
        void shouldCalculateOffsetCorrectly() {
            PageQuery query = new PageQuery();
            query.setPage(3);
            query.setSize(20);
            assertThat(query.offset()).isEqualTo(40L);
        }

        @Test
        @DisplayName("page=0 时按 1 保护，偏移量为 0")
        void shouldProtectPageLowerThanOne() {
            PageQuery query = new PageQuery();
            query.setPage(0);
            query.setSize(10);
            assertThat(query.offset()).isEqualTo(0L);
        }

        @Test
        @DisplayName("page 为负数时按 1 保护，偏移量为 0")
        void shouldProtectNegativePage() {
            PageQuery query = new PageQuery();
            query.setPage(-5);
            query.setSize(10);
            assertThat(query.offset()).isEqualTo(0L);
        }

        @Test
        @DisplayName("size=0 时按 1 保护，偏移量为 page-1")
        void shouldProtectZeroSize() {
            PageQuery query = new PageQuery();
            query.setPage(2);
            query.setSize(0);
            assertThat(query.offset()).isEqualTo(1L);
        }

        @Test
        @DisplayName("size 超过 MAX_SIZE 时按 200 截断")
        void shouldCapSizeAtMax() {
            PageQuery query = new PageQuery();
            query.setPage(2);
            query.setSize(500);
            // offset = (2-1) * min(max(500,1), 200) = 1 * 200 = 200
            assertThat(query.offset()).isEqualTo(200L);
        }

        @Test
        @DisplayName("size 为负数时按 1 保护")
        void shouldProtectNegativeSize() {
            PageQuery query = new PageQuery();
            query.setPage(3);
            query.setSize(-10);
            // offset = (3-1) * min(max(-10,1), 200) = 2 * 1 = 2
            assertThat(query.offset()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("safeOrderBy() 白名单校验")
    class SafeOrderByTest {

        @Test
        @DisplayName("orderBy 为 null 时返回默认字段")
        void shouldReturnDefaultWhenOrderByNull() {
            PageQuery query = new PageQuery();
            String result = query.safeOrderBy(Set.of("name", "age"), "id");
            assertThat(result).isEqualTo("id");
        }

        @Test
        @DisplayName("orderBy 为空字符串时返回默认字段")
        void shouldReturnDefaultWhenOrderByBlank() {
            PageQuery query = new PageQuery();
            query.setOrderBy("");
            String result = query.safeOrderBy(Set.of("name", "age"), "id");
            assertThat(result).isEqualTo("id");
        }

        @Test
        @DisplayName("orderBy 为纯空白时返回默认字段")
        void shouldReturnDefaultWhenOrderByWhitespace() {
            PageQuery query = new PageQuery();
            query.setOrderBy("   ");
            String result = query.safeOrderBy(Set.of("name", "age"), "id");
            assertThat(result).isEqualTo("id");
        }

        @Test
        @DisplayName("orderBy 在白名单内时返回 orderBy")
        void shouldReturnOrderByWhenInWhitelist() {
            PageQuery query = new PageQuery();
            query.setOrderBy("name");
            String result = query.safeOrderBy(Set.of("name", "age"), "id");
            assertThat(result).isEqualTo("name");
        }

        @Test
        @DisplayName("orderBy 不在白名单内时返回默认字段")
        void shouldReturnDefaultWhenNotInWhitelist() {
            PageQuery query = new PageQuery();
            query.setOrderBy("password");
            String result = query.safeOrderBy(Set.of("name", "age"), "id");
            assertThat(result).isEqualTo("id");
        }

        @Test
        @DisplayName("orderBy 格式不合法（含特殊字符）时返回默认字段")
        void shouldReturnDefaultWhenInvalidFormat() {
            PageQuery query = new PageQuery();
            // 含特殊字符，不符合 ORDER_BY_PATTERN
            query.setOrderBy("name; DROP TABLE");
            String result = query.safeOrderBy(Set.of("name", "age"), "id");
            assertThat(result).isEqualTo("id");
        }

        @Test
        @DisplayName("orderBy 以数字开头不符合格式时返回默认字段")
        void shouldReturnDefaultWhenStartsWithDigit() {
            PageQuery query = new PageQuery();
            query.setOrderBy("1name");
            String result = query.safeOrderBy(Set.of("name", "age"), "id");
            assertThat(result).isEqualTo("id");
        }

        @Test
        @DisplayName("白名单为 null 时返回默认字段（保守策略）")
        void shouldReturnDefaultWhenWhitelistNull() {
            PageQuery query = new PageQuery();
            query.setOrderBy("name");
            String result = query.safeOrderBy(null, "id");
            assertThat(result).isEqualTo("id");
        }

        @Test
        @DisplayName("白名单为空集合时返回默认字段（保守策略）")
        void shouldReturnDefaultWhenWhitelistEmpty() {
            PageQuery query = new PageQuery();
            query.setOrderBy("name");
            String result = query.safeOrderBy(Set.of(), "id");
            assertThat(result).isEqualTo("id");
        }

        @Test
        @DisplayName("默认字段为 null 时返回 null")
        void shouldReturnNullWhenDefaultIsNull() {
            PageQuery query = new PageQuery();
            query.setOrderBy("not_in_list");
            String result = query.safeOrderBy(Set.of("name"), null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("下划线字段名在白名单内时返回该字段")
        void shouldSupportUnderscoreFieldName() {
            PageQuery query = new PageQuery();
            query.setOrderBy("user_name");
            String result = query.safeOrderBy(Set.of("user_name", "create_time"), "id");
            assertThat(result).isEqualTo("user_name");
        }
    }

    @Nested
    @DisplayName("safeOrderDir() 排序方向安全处理")
    class SafeOrderDirTest {

        @Test
        @DisplayName("默认 orderDir=desc 返回 desc")
        void shouldReturnDescByDefault() {
            PageQuery query = new PageQuery();
            assertThat(query.safeOrderDir()).isEqualTo("desc");
        }

        @Test
        @DisplayName("orderDir=asc 返回 asc")
        void shouldReturnAscWhenSet() {
            PageQuery query = new PageQuery();
            query.setOrderDir("asc");
            assertThat(query.safeOrderDir()).isEqualTo("asc");
        }

        @Test
        @DisplayName("orderDir=ASC 大写返回 asc")
        void shouldReturnAscForUpperCase() {
            PageQuery query = new PageQuery();
            query.setOrderDir("ASC");
            assertThat(query.safeOrderDir()).isEqualTo("asc");
        }

        @Test
        @DisplayName("orderDir=DESC 大写返回 desc")
        void shouldReturnDescForUpperCase() {
            PageQuery query = new PageQuery();
            query.setOrderDir("DESC");
            assertThat(query.safeOrderDir()).isEqualTo("desc");
        }

        @Test
        @DisplayName("orderDir 为非法值时返回 desc")
        void shouldReturnDescForInvalidValue() {
            PageQuery query = new PageQuery();
            query.setOrderDir("malicious; DROP");
            assertThat(query.safeOrderDir()).isEqualTo("desc");
        }

        @Test
        @DisplayName("orderDir 为 null 时返回 desc")
        void shouldReturnDescWhenNull() {
            PageQuery query = new PageQuery();
            query.setOrderDir(null);
            assertThat(query.safeOrderDir()).isEqualTo("desc");
        }

        @Test
        @DisplayName("orderDir 为空字符串时返回 desc")
        void shouldReturnDescWhenEmpty() {
            PageQuery query = new PageQuery();
            query.setOrderDir("");
            assertThat(query.safeOrderDir()).isEqualTo("desc");
        }

        @Test
        @DisplayName("orderDir 大小写混合 Asc 返回 asc")
        void shouldReturnAscForMixedCase() {
            PageQuery query = new PageQuery();
            query.setOrderDir("Asc");
            assertThat(query.safeOrderDir()).isEqualTo("asc");
        }
    }

    @Nested
    @DisplayName("序列化能力")
    class SerializableTest {

        @Test
        @DisplayName("PageQuery 实现 Serializable")
        void shouldImplementSerializable() {
            PageQuery query = new PageQuery();
            assertThat(query).isInstanceOf(Serializable.class);
        }
    }
}
