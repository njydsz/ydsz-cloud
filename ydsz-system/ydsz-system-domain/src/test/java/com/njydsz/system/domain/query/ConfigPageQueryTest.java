package com.njydsz.system.domain.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConfigPageQuery} 单元测试。
 *
 * <p>覆盖：Lombok getter/setter、继承 PageQuery 分页字段、equals/hashCode。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("系统配置分页查询参数单元测试")
class ConfigPageQueryTest {

  @Nested
  @DisplayName("getter/setter")
  class Accessors {

    @Test
    @DisplayName("setter 正确写入业务字段")
    void should_set_business_fields() {
      ConfigPageQuery query = new ConfigPageQuery();
      query.setConfigGroup("SYSTEM");
      query.setConfigKey("max-upload-size");
      query.setStatus("ENABLED");

      assertThat(query.getConfigGroup()).isEqualTo("SYSTEM");
      assertThat(query.getConfigKey()).isEqualTo("max-upload-size");
      assertThat(query.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("继承 PageQuery 分页字段可读写")
    void should_access_page_fields() {
      ConfigPageQuery query = new ConfigPageQuery();
      query.setPageNum(2);
      query.setPageSize(20);

      assertThat(query.getPageNum()).isEqualTo(2);
      assertThat(query.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("无参构造字段为 null")
    void should_have_null_fields_by_default() {
      ConfigPageQuery query = new ConfigPageQuery();

      assertThat(query.getConfigGroup()).isNull();
      assertThat(query.getConfigKey()).isNull();
      assertThat(query.getStatus()).isNull();
    }
  }

  @Nested
  @DisplayName("SuperBuilder")
  class Builder {

    @Test
    @DisplayName("builder 构造并填充业务与分页字段")
    void should_build_with_builder() {
      ConfigPageQuery query = ConfigPageQuery.builder()
          .configGroup("SYSTEM")
          .configKey("k1")
          .pageNum(1)
          .pageSize(10)
          .build();

      assertThat(query.getConfigGroup()).isEqualTo("SYSTEM");
      assertThat(query.getConfigKey()).isEqualTo("k1");
      assertThat(query.getPageNum()).isEqualTo(1);
      assertThat(query.getPageSize()).isEqualTo(10);
    }
  }

  @Nested
  @DisplayName("equals/hashCode")
  class Equality {

    @Test
    @DisplayName("相同字段相等")
    void should_be_equal() {
      ConfigPageQuery a = ConfigPageQuery.builder().configGroup("G").configKey("K").build();
      ConfigPageQuery b = ConfigPageQuery.builder().configGroup("G").configKey("K").build();

      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("不同字段不等")
    void should_not_be_equal() {
      ConfigPageQuery a = ConfigPageQuery.builder().configKey("K1").build();
      ConfigPageQuery b = ConfigPageQuery.builder().configKey("K2").build();

      assertThat(a).isNotEqualTo(b);
    }
  }
}
