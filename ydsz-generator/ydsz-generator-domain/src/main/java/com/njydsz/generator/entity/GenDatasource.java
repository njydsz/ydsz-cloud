package com.njydsz.generator.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ydsz_gen_datasource")
public class GenDatasource {

  /** 主键 ID。 */
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
    private Boolean default;
  /** 描述。 */
  private String description;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 更新时间。 */
  private LocalDateTime updatedAt;
}
