package com.njydsz.common.search.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SearchRequest} 默认值一致性测试。
 *
 * <p>锁定两条容易被 Lombok 静默破坏的不变量：
 *
 * <ol>
 *   <li>Spring MVC 用<b>无参构造</b>绑定 {@code GET /search} 的查询对象（{@code @ModelAttribute}），
 *       Jackson 用<b>无参构造</b>反序列化 {@code @RequestBody}；若类上只有 {@code @Builder} 生成的
 *       全参构造，绑定阶段直接抛 BeanInstantiationException → HTTP 400，端点整条不可用。
 *   <li>{@code @Builder.Default} 会把字段初始化器搬进静态方法，因此无参构造必须显式补齐同样的默认值，
 *       否则集合字段变 null、{@code isHighlight}/{@code isFuzzy} 由 true 翻成 false（高亮与模糊匹配失效）。
 * </ol>
 *
 * @author ydsz-team
 * @since 26.10.26
 */
class SearchRequestDefaultTest {

  @Nested
  @DisplayName("无参构造与 builder 默认值一致")
  class DefaultParity {

    @Test
    @DisplayName("集合默认值为空集合而非 null")
    void collections_should_default_to_empty_list() {
      SearchRequest bound = new SearchRequest();
      SearchRequest built = SearchRequest.builder().build();

      assertThat(bound.getTypes()).isNotNull().isEmpty();
      assertThat(bound.getFilters()).isNotNull().isEmpty();
      assertThat(bound.getAggregations()).isNotNull().isEmpty();
      assertThat(bound.getRoles()).isNotNull().isEmpty();
      assertThat(bound.getTypes()).isEqualTo(built.getTypes());
      assertThat(bound.getFilters()).isEqualTo(built.getFilters());
      assertThat(bound.getAggregations()).isEqualTo(built.getAggregations());
      assertThat(bound.getRoles()).isEqualTo(built.getRoles());
    }

    @Test
    @DisplayName("布尔默认值与 builder 相同（highlight/fuzzy 开启）")
    void booleans_should_match_builder_defaults() {
      SearchRequest bound = new SearchRequest();
      SearchRequest built = SearchRequest.builder().build();

      assertThat(bound.isHighlight()).isEqualTo(built.isHighlight());
      assertThat(bound.isFuzzy()).isEqualTo(built.isFuzzy());
      assertThat(bound.isAscending()).isEqualTo(built.isAscending());
      assertThat(bound.isTitleOnly()).isEqualTo(built.isTitleOnly());
      assertThat(bound.isHighlight()).isTrue();
      assertThat(bound.isFuzzy()).isTrue();
      assertThat(bound.isAscending()).isFalse();
      assertThat(bound.isTitleOnly()).isFalse();
    }
  }

  @Nested
  @DisplayName("builder 显式值不被默认值覆盖")
  class ExplicitValues {

    @Test
    @DisplayName("builder 关闭高亮时保持 false")
    void builder_should_respect_explicit_false() {
      SearchRequest built = SearchRequest.builder().isHighlight(false).isFuzzy(false).build();

      assertThat(built.isHighlight()).isFalse();
      assertThat(built.isFuzzy()).isFalse();
    }

    @Test
    @DisplayName("无参构造与 builder 各自持有独立集合实例")
    void default_collections_should_not_be_shared() {
      SearchRequest first = new SearchRequest();
      SearchRequest second = new SearchRequest();

      first.getTypes().add("doc");

      assertThat(second.getTypes()).isEmpty();
      assertThat(second.getTypes()).isNotSameAs(first.getTypes());
    }
  }
}
