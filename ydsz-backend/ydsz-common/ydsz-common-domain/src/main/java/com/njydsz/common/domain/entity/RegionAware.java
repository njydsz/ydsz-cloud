package com.njydsz.common.domain.entity;

import java.io.Serializable;

/**
 * 区域维度标记接口
 *
 * <p>标识该实体支持区域维度数据隔离。
 * 配合 SQL 拦截器自动注入 region_id 条件。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * // 接口 + 组合
 * public class Store extends BaseEntity<Long> implements RegionAware {
 *     @TableField("region_id")
 *     private Long regionId;
 *     private String storeName;
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
 * @see TenantAware
 * @see ProjectAware
 */
public interface RegionAware extends Serializable {

    /**
     * 获取区域 ID
     *
     * @return 区域 ID
     */
    Long getRegionId();

    /**
     * 设置区域 ID
     *
     * @param regionId 区域 ID
     */
    void setRegionId(Long regionId);
}
