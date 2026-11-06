package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GenDatasource} 单元测试。
 *
 * <p>覆盖：业务字段 getter/setter、password 不进 toString/equals/hashCode、@TableName 注解。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("数据源配置实体")
class GenDatasourceTest {

  @Nested
  @DisplayName("业务字段")
  class BusinessFields {

    @Test
    @DisplayName("setter 写入后可读取")
    void accessors() {
      GenDatasource ds = new GenDatasource();
      ds.setName("主库");
      ds.setJdbcUrl("jdbc:postgresql://localhost:5432/db");
      ds.setUsername("admin");
      ds.setPassword("secret");
      ds.setDialect("postgresql");
      ds.setIsDefault(true);
      ds.setDescription("生产库");

      assertThat(ds.getName()).isEqualTo("主库");
      assertThat(ds.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/db");
      assertThat(ds.getUsername()).isEqualTo("admin");
      assertThat(ds.getPassword()).isEqualTo("secret");
      assertThat(ds.getDialect()).isEqualTo("postgresql");
      assertThat(ds.getIsDefault()).isTrue();
      assertThat(ds.getDescription()).isEqualTo("生产库");
    }
  }

  @Nested
  @DisplayName("password 脱敏")
  class PasswordMasking {

    @Test
    @DisplayName("toString 不包含 password 值")
    void toStringExcludesPassword() {
      GenDatasource ds = new GenDatasource();
      ds.setName("主库");
      ds.setPassword("secret123");
      assertThat(ds.toString()).doesNotContain("secret123");
    }

    @Test
    @DisplayName("equals 忽略 password 差异")
    void equalsIgnoresPassword() {
      GenDatasource a = new GenDatasource();
      a.setName("db");
      a.setJdbcUrl("jdbc:mysql://h/db");
      a.setUsername("u");
      a.setPassword("p1");

      GenDatasource b = new GenDatasource();
      b.setName("db");
      b.setJdbcUrl("jdbc:mysql://h/db");
      b.setUsername("u");
      b.setPassword("p2");

      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("password 不同但其他字段相同仍 equals")
    void equalsWithDifferentPassword() {
      GenDatasource a = new GenDatasource();
      a.setId("1");
      a.setPassword("p1");
      GenDatasource b = new GenDatasource();
      b.setId("1");
      b.setPassword("p2");
      assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("其他字段不同则 not equals")
    void notEqualsWithDifferentField() {
      GenDatasource a = new GenDatasource();
      a.setName("db1");
      GenDatasource b = new GenDatasource();
      b.setName("db2");
      assertThat(a).isNotEqualTo(b);
    }
  }

  @Nested
  @DisplayName("@TableName 注解")
  class TableNameAnnotation {

    @Test
    @DisplayName("表名为 ydsz_gen_datasource")
    void tableName() {
      TableName annotation = GenDatasource.class.getAnnotation(TableName.class);
      assertThat(annotation).isNotNull();
      assertThat(annotation.value()).isEqualTo("ydsz_gen_datasource");
    }
  }
}
