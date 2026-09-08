package com.njydsz.generator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

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
public class GenDatasource extends MpBaseAuditEntity<Long> {

  /** 主键 ID（AUTO 自增，覆盖基类 ASSIGN_ID）。 */
  @TableId(type = IdType.AUTO)
  private Long id;
  /** 数据源名称。 */
  private String name;
  /** JDBC URL。 */
  private String jdbcUrl;
  /** 用户名。 */
  private String username;
  /** 密码（加密存储，不序列化到外向响应）。 */
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private String password;
  /** 数据库方言。 */
  private String dialect;
  /** 是否默认数据源。 */
  @TableField("is_default")
  private Boolean isDefault;
  /** 描述。 */
  private String description;
}
