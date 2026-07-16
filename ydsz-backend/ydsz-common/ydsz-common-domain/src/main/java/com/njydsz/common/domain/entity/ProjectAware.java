package com.njydsz.common.domain.entity;

import java.io.Serializable;

/**
 * 项目维度标记接口
 *
 * <p>标识该实体支持项目维度数据隔离。业务实体可通过实现此接口替代继承 {@link ProjectEntity}。
 * 配合 SQL 拦截器自动注入project_id 条件
 *
 * <p><b>迁移策略：</b>
 * <pre>{@code
 * // 旧写法（继承。
 * public class Task extends ProjectEntity<Long> {
 *     private String taskName;
 * }
 *
 * // 新写法（接口 + 组合。
 * public class Task extends BaseEntity<Long> implements ProjectAware {
 *     @TableField("project_id")
 *     private Long projectId;
 *     private String taskName;
 * }
 * }</pre>
 *
 * <p><b>多维度组合示例：</b>
 * <pre>{@code
 * // 同时支持租户 + 项目 + 区域三个维度
 * public class Order extends BaseEntity<Long>
 *         implements TenantAware, ProjectAware, RegionAware {
 *     @TableField("tenant_id")
 *     private Long tenantId;
 *     @TableField("project_id")
 *     private Long projectId;
 *     @TableField("region_id")
 *     private Long regionId;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see ProjectEntity
 * @see TenantAware
 * @see RegionAware
 */
public interface ProjectAware extends Serializable {

    /**
     * 获取项目 ID
     *
     * @return 项目 ID
     */
    Long getProjectId();

    /**
     * 设置项目 ID
     *
     * @param projectId 项目 ID
     */
    void setProjectId(Long projectId);
}
