package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;


/**
 * 数据源配置领域实体。
 *
 * <p>对应 ydsz_gen_datasource 表。
 * <p>密码字段不会出现在 {@code toString} 和 {@code equals/hashCode} 中，
 * 防止序列化/比较时意外泄露。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@SuperBuilder
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_gen_datasource")
public class GenDatasource extends MpBaseEntity<String> {

  /** 数据源名称。 */
  @NotBlank(message = "{generator.datasource.name.notblank}")
  @Size(max = 64, message = "{generator.datasource.name.size}")
  private String name;
  /** JDBC URL。 */
  @NotBlank(message = "{generator.datasource.jdbcUrl.notblank}")
  @Size(max = 512, message = "{generator.datasource.jdbcUrl.size}")
  private String jdbcUrl;
  /** 用户名。 */
  @NotBlank(message = "{generator.datasource.username.notblank}")
  @Size(max = 64, message = "{generator.datasource.username.size}")
  private String username;
  /** 密码（加密存储，不序列化到外向响应）。 */
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  @Size(max = 128, message = "{generator.datasource.password.size}")
  private String password;
  /** 数据库方言。 */
  @Size(max = 32, message = "{generator.datasource.dialect.size}")
  private String dialect;
  /** 是否默认数据源。 */
  @TableField("is_default")
  private Boolean isDefault;
  /** 描述。 */
  private String description;
}
