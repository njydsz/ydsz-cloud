package com.njydsz.common.jdbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 字段自动填充配置。
 *
 * <p>控制 MyBatis-Plus MetaObjectHandler 自动填充字段的行为：创建人、更新人、创建时间、更新时间。
 *
 * <p>通过 {@code ydsz.jdbc.field-fill.*} 配置各字段是否启用、是否覆盖。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.jdbc.field-fill")
public class FieldFillConfiguration {

  private InterceptConfig createdByIntercept = new InterceptConfig();
  private InterceptConfig updateByIntercept = new InterceptConfig();
  private InterceptConfig createAtIntercept = new InterceptConfig();
  private InterceptConfig updateAtIntercept = new InterceptConfig();

  public InterceptConfig getCreatedByIntercept() { return createdByIntercept; }
  public void setCreatedByIntercept(InterceptConfig createdByIntercept) { this.createdByIntercept = createdByIntercept; }

  public InterceptConfig getUpdateByIntercept() { return updateByIntercept; }
  public void setUpdateByIntercept(InterceptConfig updateByIntercept) { this.updateByIntercept = updateByIntercept; }

  public InterceptConfig getCreateAtIntercept() { return createAtIntercept; }
  public void setCreateAtIntercept(InterceptConfig createAtIntercept) { this.createAtIntercept = createAtIntercept; }

  public InterceptConfig getUpdateAtIntercept() { return updateAtIntercept; }
  public void setUpdateAtIntercept(InterceptConfig updateAtIntercept) { this.updateAtIntercept = updateAtIntercept; }
}
