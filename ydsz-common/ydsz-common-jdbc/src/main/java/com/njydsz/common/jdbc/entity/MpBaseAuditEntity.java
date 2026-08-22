package com.njydsz.common.jdbc.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.json.annotation.JsonFormat;

/**
 * MyBatis-Plus 增强版审计基础实体
 *
 * <p>包含审计字段（创建人/时间、更新人/时间），由 {@code CombinedFieldFillInterceptor} 通过 JSQLParser 改写 SQL 在
 * INSERT/UPDATE 时自动填充。
 *
 * <p>注意：审计字段不标注 {@code @TableField(fill)}，以避免与 SQL 层拦截器的双重填充冲突。
 *
 * <p><b>1.0.0</b>：不再继承 common-domain 的 BaseAuditEntity，字段内联自洽， 业务模块实体仅依赖 ydsz-common-jdbc 一个模块。
 *
 * @param <T> 主键ID类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class MpBaseAuditEntity<T extends Serializable> extends MpBaseIdEntity<T> {

  private static final long serialVersionUID = 1L;

  /**
   * 创建人ID
   *
   * <p>由 {@code CombinedFieldFillInterceptor} 在 INSERT 操作时自动填充， 此处不使用 {@code @TableField(fill)}
   * 以避免双重填充。
   */
  @TableField(value = "created_by")
  private String createdBy;

  /**
   * 创建时间
   *
   * <p>由 {@code CombinedFieldFillInterceptor} 在 INSERT 操作时自动填充， 此处不使用 {@code @TableField(fill)}
   * 以避免双重填充。
   */
  @TableField(value = "created_at")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createdAt;

  /**
   * 更新人ID
   *
   * <p>由 {@code CombinedFieldFillInterceptor} 在 INSERT/UPDATE 操作时自动填充， 此处不使用
   * {@code @TableField(fill)} 以避免双重填充。
   */
  @TableField(value = "updated_by")
  private String updatedBy;

  /**
   * 更新时间
   *
   * <p>由 {@code CombinedFieldFillInterceptor} 在 INSERT/UPDATE 操作时自动填充， 此处不使用
   * {@code @TableField(fill)} 以避免双重填充。
   */
  @TableField(value = "updated_at")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime updatedAt;
}
