package com.njydsz.pmis.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PageResult} 分页结果封装测试
 *
 * <p>覆盖构造方法、工厂方法、总页数计算、空结果处理、MyBatis-Plus Page 转换等核心逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PageResult 分页结果封装测试")
class PageResultTest {

    @Nested
    @DisplayName("默认构造方法")
    class DefaultConstructorTest {

        @Test
        @DisplayName("默认构造初始化空列表且数值为 0")
        void shouldInitializeWithEmptyList() {
            PageResult<String> result = new PageResult<>();
            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
            assertThat(result.getPage()).isEqualTo(0L);
            assertThat(result.getSize()).isEqualTo(0L);
            assertThat(result.getPages()).isEqualTo(0L);
        }

        @Test
        @DisplayName("默认构造的 list 不可变")
        void shouldReturnImmutableEmptyList() {
            PageResult<String> result = new PageResult<>();
            assertThat(result.getList()).isSameAs(Collections.emptyList());
        }
    }

    @Nested
    @DisplayName("全参构造方法")
    class FullConstructorTest {

        @Test
        @DisplayName("正确设置所有字段并计算总页数")
        void shouldSetAllFieldsAndCalculatePages() {
            List<String> data = List.of("a", "b", "c");
            PageResult<String> result = new PageResult<>(data, 100L, 2L, 10L);

            assertThat(result.getList()).isEqualTo(data);
            assertThat(result.getTotal()).isEqualTo(100L);
            assertThat(result.getPage()).isEqualTo(2L);
            assertThat(result.getSize()).isEqualTo(10L);
            assertThat(result.getPages()).isEqualTo(10L);
        }

        @Test
        @DisplayName("总数不能被 size 整除时向上取整")
        void shouldCeilPagesWhenNotDivisible() {
            PageResult<String> result = new PageResult<>(List.of("a"), 95L, 10L, 10L);
            // 95 / 10 = 9.5 → 10 页
            assertThat(result.getPages()).isEqualTo(10L);
        }

        @Test
        @DisplayName("总数为 0 时总页数为 0")
        void shouldReturnZeroPagesWhenTotalZero() {
            PageResult<String> result = new PageResult<>(Collections.emptyList(), 0L, 1L, 10L);
            assertThat(result.getPages()).isEqualTo(0L);
        }

        @Test
        @DisplayName("size=0 时总页数为 0（防除零）")
        void shouldReturnZeroPagesWhenSizeZero() {
            PageResult<String> result = new PageResult<>(Collections.emptyList(), 100L, 1L, 0L);
            assertThat(result.getPages()).isEqualTo(0L);
        }

        @Test
        @DisplayName("总数刚好被 size 整除时页数准确")
        void shouldReturnExactPagesWhenDivisible() {
            PageResult<String> result = new PageResult<>(List.of("a"), 100L, 1L, 20L);
            assertThat(result.getPages()).isEqualTo(5L);
        }

        @Test
        @DisplayName("list 为 null 时保留 null（不主动替换）")
        void shouldKeepNullList() {
            PageResult<String> result = new PageResult<>(null, 0L, 1L, 10L);
            assertThat(result.getList()).isNull();
        }
    }

    @Nested
    @DisplayName("empty() 工厂方法")
    class EmptyFactoryTest {

        @Test
        @DisplayName("empty() 返回第 1 页、size=10、总数 0 的空结果")
        void shouldReturnEmptyResult() {
            PageResult<String> result = PageResult.empty();

            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
            assertThat(result.getPage()).isEqualTo(1L);
            assertThat(result.getSize()).isEqualTo(10L);
            assertThat(result.getPages()).isEqualTo(0L);
        }

        @Test
        @DisplayName("empty() 多次调用返回独立实例")
        void shouldReturnDifferentInstances() {
            PageResult<String> r1 = PageResult.empty();
            PageResult<String> r2 = PageResult.empty();
            assertThat(r1).isNotSameAs(r2);
        }
    }

    @Nested
    @DisplayName("of() 工厂方法")
    class OfFactoryTest {

        @Test
        @DisplayName("of() 等价于全参构造")
        void shouldEquivalentToFullConstructor() {
            List<Integer> data = List.of(1, 2, 3);
            PageResult<Integer> result = PageResult.of(data, 50L, 5L, 10L);

            assertThat(result.getList()).isEqualTo(data);
            assertThat(result.getTotal()).isEqualTo(50L);
            assertThat(result.getPage()).isEqualTo(5L);
            assertThat(result.getSize()).isEqualTo(10L);
            assertThat(result.getPages()).isEqualTo(5L);
        }

        @Test
        @DisplayName("of() 支持空数据列表")
        void shouldSupportEmptyList() {
            PageResult<String> result = PageResult.of(Collections.emptyList(), 0L, 1L, 10L);
            assertThat(result.getList()).isEmpty();
            assertThat(result.getPages()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("ofPage() 从 MyBatis-Plus Page 转换")
    class OfPageTest {

        @Test
        @DisplayName("Page 为 null 时返回 empty 结果")
        void shouldReturnEmptyWhenPageNull() {
            PageResult<String> result = PageResult.ofPage(null);
            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
            assertThat(result.getPage()).isEqualTo(1L);
            assertThat(result.getSize()).isEqualTo(10L);
        }

        @Test
        @DisplayName("正确转换 Page 的各字段")
        void shouldConvertPageFields() {
            Page<String> page = new Page<>(3L, 20L);
            page.setTotal(55L);
            page.setRecords(List.of("x", "y"));

            PageResult<String> result = PageResult.ofPage(page);

            assertThat(result.getList()).containsExactly("x", "y");
            assertThat(result.getTotal()).isEqualTo(55L);
            assertThat(result.getPage()).isEqualTo(3L);
            assertThat(result.getSize()).isEqualTo(20L);
            // 55 / 20 = 2.75 → 3 页
            assertThat(result.getPages()).isEqualTo(3L);
        }

        @Test
        @DisplayName("空 Page 转换为空结果")
        void shouldConvertEmptyPage() {
            Page<String> page = new Page<>(1L, 10L);
            page.setTotal(0L);
            page.setRecords(Collections.emptyList());

            PageResult<String> result = PageResult.ofPage(page);

            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
            assertThat(result.getPages()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("序列化能力")
    class SerializableTest {

        @Test
        @DisplayName("PageResult 实现 Serializable")
        void shouldImplementSerializable() {
            PageResult<String> result = PageResult.empty();
            assertThat(result).isInstanceOf(Serializable.class);
        }
    }

    @Nested
    @DisplayName("Lombok @Data 行为")
    class DataBehaviorTest {

        @Test
        @DisplayName("setter/getter 正常工作")
        void shouldSetAndGetFields() {
            PageResult<String> result = new PageResult<>();
            result.setList(List.of("a"));
            result.setTotal(1L);
            result.setPage(1L);
            result.setSize(1L);
            result.setPages(1L);

            assertThat(result.getList()).containsExactly("a");
            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getPage()).isEqualTo(1L);
            assertThat(result.getSize()).isEqualTo(1L);
            assertThat(result.getPages()).isEqualTo(1L);
        }

        @Test
        @DisplayName("equals/hashCode 基于所有字段")
        void shouldHaveEqualsAndHashCode() {
            PageResult<String> r1 = PageResult.of(List.of("a"), 10L, 1L, 10L);
            PageResult<String> r2 = PageResult.of(List.of("a"), 10L, 1L, 10L);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }
    }
}
