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

import lombok.Data;
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
@Data
@ConfigurationProperties(prefix = "ydsz.jdbc.data-permission")
public class DataPermissionConfiguration {
  /** 是否启用数据权限拦截（行级 + 列级）。 */
  private Boolean enabled = false;

  /** 拦截表策略：INCLUDE 仅拦截配置表；EXCLUDE 排除配置表。 */
  private InterceptTableStrategy interceptTableStrategy = InterceptTableStrategy.EXCLUDE;

  /** 与 {@link #interceptTableStrategy} 配合使用的表清单（忽略大小写）。 */
  private Set<String> tables = new HashSet<>(16);

  /**
   * 行级权限字段映射：Header -> 列名。
   *
   * <p><b>注意：</b>租户隔离（TENANT 维度）已由独立的 {@code common-tenant} 模块 通过 {@code
   * TenantIsolationInterceptor} 处理，不再在此配置。
   */
  /** 公司列名，对应数据权限维度 GROUP */
  private String companyColumn = "company_id";

  /** 部门列名，对应数据权限维度 COMPANY/DEPT */
  private String deptColumn = "dept_id";

  /** 用户列名，对应数据权限维度 USER */
  private String userColumn = "user_id";

  /** 项目列名，对应数据权限维度 PROJECT */
  private String projectColumn = "project_id";

  /** 区域列名，对应数据权限维度 REGION */
  private String regionColumn = "region_id";
}
