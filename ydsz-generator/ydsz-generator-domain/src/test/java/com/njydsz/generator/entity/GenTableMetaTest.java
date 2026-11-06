package com.njydsz.generator.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GenTableMeta} 单元测试。
 *
 * <p>覆盖：业务字段 getter/setter、MpBaseEntity 继承字段、SuperBuilder 构造、@TableName 注解。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("表元数据实体")
class GenTableMetaTest {

  @Nested
  @DisplayName("业务字段")
  class BusinessFields {

    @Test
    @DisplayName("setter 写入后可读取")
    void accessors() {
      GenTableMeta meta = new GenTableMeta();
      LocalDateTime cachedAt = LocalDateTime.of(2026, 11, 6, 10, 0);
      meta.setDatasourceId("ds-1");
      meta.setTableName("ydsz_user");
      meta.setComment("用户表");
      meta.setAliasName("User");
      meta.setModuleName("system");
      meta.setCachedAt(cachedAt);

      assertThat(meta.getDatasourceId()).isEqualTo("ds-1");
      assertThat(meta.getTableName()).isEqualTo("ydsz_user");
      assertThat(meta.getComment()).isEqualTo("用户表");
      assertThat(meta.getAliasName()).isEqualTo("User");
      assertThat(meta.getModuleName()).isEqualTo("system");
      assertThat(meta.getCachedAt()).isEqualTo(cachedAt);
    }
  }

  @Nested
  @DisplayName("MpBaseEntity 继承字段")
  class BaseFields {

    @Test
    @DisplayName("基类字段可读写")
    void baseAccessors() {
      GenTableMeta meta = new GenTableMeta();
      meta.setId("id-1");
      meta.setSort(5);
      meta.setStatus("1");
      meta.setTenantId("tenant-1");
      meta.setIsDeleted(false);
      meta.setRevision(2);

      assertThat(meta.getId()).isEqualTo("id-1");
      assertThat(meta.getSort()).isEqualTo(5);
      assertThat(meta.getStatus()).isEqualTo("1");
      assertThat(meta.getTenantId()).isEqualTo("tenant-1");
      assertThat(meta.getIsDeleted()).isFalse();
      assertThat(meta.getRevision()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("SuperBuilder")
  class Builder {

    @Test
    @DisplayName("builder 构造业务字段与基类字段")
    void builderConstructs() {
      LocalDateTime cachedAt = LocalDateTime.of(2026, 11, 6, 10, 0);
      GenTableMeta meta =
          GenTableMeta.builder()
              .id("id-2")
              .tableName("ydsz_order")
              .aliasName("Order")
              .moduleName("trade")
              .cachedAt(cachedAt)
              .tenantId("tenant-2")
              .build();

      assertThat(meta.getId()).isEqualTo("id-2");
      assertThat(meta.getTableName()).isEqualTo("ydsz_order");
      assertThat(meta.getAliasName()).isEqualTo("Order");
      assertThat(meta.getModuleName()).isEqualTo("trade");
      assertThat(meta.getCachedAt()).isEqualTo(cachedAt);
      assertThat(meta.getTenantId()).isEqualTo("tenant-2");
    }
  }

  @Nested
  @DisplayName("@TableName 注解")
  class TableNameAnnotation {

    @Test
    @DisplayName("表名为 ydsz_gen_table_meta")
    void tableName() {
      TableName annotation = GenTableMeta.class.getAnnotation(TableName.class);
      assertThat(annotation).isNotNull();
      assertThat(annotation.value()).isEqualTo("ydsz_gen_table_meta");
    }
  }
}
