package com.njydsz.common.jdbc.config;

/**
 * 数据权限自动配置类。
 *
 * <p>配置数据权限拦截器所需的列名映射、默认策略和过滤规则， 通过 Spring Boot 配置属性绑定实现灵活的数据权限控制。
 *
 * <p><b>配置前缀：</b>{@code ydsz.jdbc.data-permission}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.jdbc.enums.InterceptTableStrategy;

/**
 * DataPermissionConfiguration 自动配置类，注册模块 Bean 并管理装配条件。
 *
 * <p>所属包：{@code com.njydsz.common.jdbc.config}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.jdbc.data-permission")
public class DataPermissionConfiguration {
  private Boolean isEnabled = false;
  private InterceptTableStrategy interceptTableStrategy = InterceptTableStrategy.EXCLUDE;
  private Set<String> tables = new HashSet<>(16);
  private String companyColumn = "company_id";
  private String deptColumn = "dept_id";
  private String userColumn = "user_id";
  private String projectColumn = "project_id";
  private String regionColumn = "region_id";
  private String spaceColumn = "space_id";

  public Boolean getIsEnabled() { return isEnabled; }
  public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

  public InterceptTableStrategy getInterceptTableStrategy() { return interceptTableStrategy; }
  public void setInterceptTableStrategy(InterceptTableStrategy interceptTableStrategy) { this.interceptTableStrategy = interceptTableStrategy; }

  public Set<String> getTables() { return tables; }
  public void setTables(Set<String> tables) { this.tables = tables; }

  public String getCompanyColumn() { return companyColumn; }
  public void setCompanyColumn(String companyColumn) { this.companyColumn = companyColumn; }

  public String getDeptColumn() { return deptColumn; }
  public void setDeptColumn(String deptColumn) { this.deptColumn = deptColumn; }

  public String getUserColumn() { return userColumn; }
  public void setUserColumn(String userColumn) { this.userColumn = userColumn; }

  public String getProjectColumn() { return projectColumn; }
  public void setProjectColumn(String projectColumn) { this.projectColumn = projectColumn; }

  public String getRegionColumn() { return regionColumn; }
  public void setRegionColumn(String regionColumn) { this.regionColumn = regionColumn; }

  public String getSpaceColumn() { return spaceColumn; }
  public void setSpaceColumn(String spaceColumn) { this.spaceColumn = spaceColumn; }
}
