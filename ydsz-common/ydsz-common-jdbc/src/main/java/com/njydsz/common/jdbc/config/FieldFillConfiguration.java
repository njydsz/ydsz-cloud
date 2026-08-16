package com.njydsz.common.jdbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * 字段自动填充配置。
 *
 * <p>控制 MyBatis-Plus MetaObjectHandler 自动填充字段的行为：创建人、更新人、创建时间、更新时间。
 *
 * <p>通过 {@code ydsz.jdbc.field-fill.*} 配置各字段是否启用、是否覆盖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.jdbc.field-fill")
public class FieldFillConfiguration {

  /** 创建人字段填充配置 */
  private InterceptConfig createdByIntercept = new InterceptConfig();

  /** 更新人字段填充配置 */
  private InterceptConfig updateByIntercept = new InterceptConfig();

  /** 创建时间字段填充配置 */
  private InterceptConfig createAtIntercept = new InterceptConfig();

  /** 更新时间字段填充配置 */
  private InterceptConfig updateAtIntercept = new InterceptConfig();
}
