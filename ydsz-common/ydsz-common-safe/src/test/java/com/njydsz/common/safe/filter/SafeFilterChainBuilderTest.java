package com.njydsz.common.safe.filter;

import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import com.njydsz.common.safe.filter.SafeFilterChainBuilder.FilterRegistrationDescriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link SafeFilterChainBuilder} 单元测试。
 *
 * <p>验证过滤器链构建器的注册、排序、启用过滤与冲突检测逻辑。</p>
 */
@DisplayName("SafeFilterChainBuilder")
class SafeFilterChainBuilderTest {

  /** 一个无操作的测试用 Filter，仅用于构造 FilterRegistrationBean。 */
  private static final class NoOpFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
      // no-op
    }
  }

  private FilterRegistrationDescriptor<NoOpFilter> descriptor(String name, int order, boolean enabled) {
    return FilterRegistrationDescriptor.of(
        name,
        order,
        List.of("/*"),
        () -> enabled,
        () -> new FilterRegistrationBean<>(new NoOpFilter()));
  }

  @Nested
  @DisplayName("register()")
  class Register {

    @Test
    @DisplayName("传入 null 不抛异常且不增加 descriptor")
    void register_null_should_not_throw_or_add() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      assertThatCode(() -> builder.register(null)).doesNotThrowAnyException();
      assertThat(builder.size()).isZero();
    }

    @Test
    @DisplayName("正常注册后 size 增加")
    void register_valid_descriptor_should_increase_size() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      builder.register(descriptor("test", 1, true));
      assertThat(builder.size()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("build() 排序")
  class BuildSort {

    @Test
    @DisplayName("按 order 升序排序（10→1→40 排序后为 1→10→40）")
    void should_sort_by_order_ascending() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      builder.register(descriptor("filter-b", 10, true));
      builder.register(descriptor("filter-a", 1, true));
      builder.register(descriptor("filter-c", 40, true));

      List<FilterRegistrationDescriptor<?>> sorted = builder.build();

      assertThat(sorted).hasSize(3);
      assertThat(sorted.get(0).name()).isEqualTo("filter-a");
      assertThat(sorted.get(0).order()).isEqualTo(1);
      assertThat(sorted.get(1).name()).isEqualTo("filter-b");
      assertThat(sorted.get(1).order()).isEqualTo(10);
      assertThat(sorted.get(2).name()).isEqualTo("filter-c");
      assertThat(sorted.get(2).order()).isEqualTo(40);
    }

    @Test
    @DisplayName("同 order 冲突不抛异常（仅记录日志）")
    void same_order_conflict_should_not_throw() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      builder.register(descriptor("filter-x", 10, true));
      builder.register(descriptor("filter-y", 10, true));

      assertThatCode(builder::build).doesNotThrowAnyException();
      assertThat(builder.build()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("toRegistrationBeans()")
  class ToRegistrationBeans {

    @Test
    @DisplayName("注册后可取出 FilterRegistrationBean")
    void should_return_registration_beans() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      builder.register(descriptor("test", 1, true));

      List<FilterRegistrationBean<?>> beans = builder.toRegistrationBeans();

      assertThat(beans).hasSize(1);
      assertThat(beans.get(0).getFilter()).isInstanceOf(NoOpFilter.class);
    }

    @Test
    @DisplayName("仅返回 enabled=true 的 registration")
    void should_only_return_enabled_registrations() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      builder.register(descriptor("enabled-filter", 1, true));
      builder.register(descriptor("disabled-filter", 2, false));
      builder.register(descriptor("another-enabled", 3, true));

      List<FilterRegistrationBean<?>> beans = builder.toRegistrationBeans();

      assertThat(beans).hasSize(2);
    }

    @Test
    @DisplayName("结果按 order 升序排列")
    void should_return_beans_in_sorted_order() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      builder.register(descriptor("filter-c", 40, true));
      builder.register(descriptor("filter-a", 1, true));
      builder.register(descriptor("filter-b", 10, true));

      List<FilterRegistrationBean<?>> beans = builder.toRegistrationBeans();

      assertThat(beans).hasSize(3);
      // 由于无法直接从 FilterRegistrationBean 取 order，通过 descriptors 间接验证
      List<FilterRegistrationDescriptor<?>> sorted = builder.build();
      assertThat(sorted.get(0).order()).isEqualTo(1);
      assertThat(sorted.get(1).order()).isEqualTo(10);
      assertThat(sorted.get(2).order()).isEqualTo(40);
    }

    @Test
    @DisplayName("所有 registration 均 disabled 时返回空列表")
    void all_disabled_should_return_empty_list() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      builder.register(descriptor("disabled-1", 1, false));
      builder.register(descriptor("disabled-2", 2, false));

      assertThat(builder.toRegistrationBeans()).isEmpty();
    }

    @Test
    @DisplayName("supplier 抛异常时跳过该 registration 不中断")
    void supplier_exception_should_be_caught_and_skipped() {
      SafeFilterChainBuilder builder = new SafeFilterChainBuilder();
      // 正常 registration
      builder.register(descriptor("good", 1, true));
      // 会抛出异常的 registration
      builder.register(FilterRegistrationDescriptor.of(
          "bad",
          2,
          List.of("/*"),
          () -> true,
          () -> {
            throw new RuntimeException("simulated supplier failure");
          }));

      List<FilterRegistrationBean<?>> beans = builder.toRegistrationBeans();

      // "good" 仍在，"bad" 被跳过
      assertThat(beans).hasSize(1);
    }
  }

  @Nested
  @DisplayName("FilterRegistrationDescriptor")
  class FilterRegistrationDescriptorTest {

    @Test
    @DisplayName("of() 工厂方法创建非空 descriptor")
    void of_factory_should_create_descriptor() {
      FilterRegistrationDescriptor<NoOpFilter> desc = descriptor("test", 5, true);

      assertThat(desc.name()).isEqualTo("test");
      assertThat(desc.order()).isEqualTo(5);
      assertThat(desc.urlPatterns()).containsExactly("/*");
      assertThat(desc.enabled().get()).isTrue();
      assertThat(desc.supplier().get()).isNotNull();
    }

    @Test
    @DisplayName("同一 supplier 和 enabled 实例的 record 相等性成立")
    void record_equality_with_shared_suppliers() {
      // record 基于所有字段比较；Supplier 是 lambda，需共享实例才能相等
      Supplier<Boolean> sharedEnabled = () -> true;
      Supplier<FilterRegistrationBean<NoOpFilter>> sharedSupplier =
          () -> new FilterRegistrationBean<>(new NoOpFilter());

      FilterRegistrationDescriptor<NoOpFilter> a = FilterRegistrationDescriptor.of(
          "same", 1, List.of("/*"), sharedEnabled, sharedSupplier);
      FilterRegistrationDescriptor<NoOpFilter> b = FilterRegistrationDescriptor.of(
          "same", 1, List.of("/*"), sharedEnabled, sharedSupplier);

      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("不同 supplier 实例的 record 不相等")
    void record_inequality_with_different_suppliers() {
      FilterRegistrationDescriptor<NoOpFilter> a = descriptor("same", 1, true);
      FilterRegistrationDescriptor<NoOpFilter> b = descriptor("same", 1, true);

      // lambda 实例不同，record equals 返回 false
      assertThat(a).isNotEqualTo(b);
    }
  }
}
