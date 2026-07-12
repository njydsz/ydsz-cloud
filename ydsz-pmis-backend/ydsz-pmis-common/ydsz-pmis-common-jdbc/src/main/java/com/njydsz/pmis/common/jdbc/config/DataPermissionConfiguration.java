package com.njydsz.pmis.common.jdbc.config;

/**
 * 数据权限自动配置类。
 *
 * <p>配置数据权限拦截器所需的列名映射、默认策略和过滤规则，
 * 通过 Spring Boot 配置属性绑定实现灵活的数据权限控制。
 *
 * <p><b>配置前缀：</b>{@code remi.jdbc.data-permission}
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */

import com.njydsz.pmis.common.jdbc.enums.InterceptTableStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "remi.jdbc.data-permission")
public class DataPermissionConfiguration {
    /**
     * 是否启用数据权限拦截（行级 + 列级）。
     */
    private Boolean enabled = false;

    /**
     * 拦截表策略：INCLUDE 仅拦截配置表；EXCLUDE 排除配置表。
     */
    private InterceptTableStrategy interceptTableStrategy = InterceptTableStrategy.EXCLUDE;

    /**
     * 与 {@link #interceptTableStrategy} 配合使用的表清单（忽略大小写）。
     */
    private Set<String> tables = new HashSet<>();

    /**
     * 行级权限字段映射：Header -> 列名。
     */
    /** 租户列名，对应数据权限维度 TENANT */
    private String tenantColumn = "tenant_id";
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
