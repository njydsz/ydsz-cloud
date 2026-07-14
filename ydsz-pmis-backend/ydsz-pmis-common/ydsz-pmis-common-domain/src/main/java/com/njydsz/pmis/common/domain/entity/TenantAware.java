package com.njydsz.pmis.common.domain.entity;

import java.io.Serializable;

/**
 * 租户维度标记接口
 *
 * <p>标识该实体支持多租户数据隔离。业务实体可通过实现此接口替代继。?{@link TenantEntity}。?
 * 配合 SQL 拦截器自动注。?tenant_id 条件。?
 *
 * <p><b>迁移策略。?/b>
 * <pre>{@code
 * // 旧写法（继承。?
 * public class Product extends TenantEntity<Long> {
 *     private String productName;
 * }
 *
 * // 新写法（接口 + 组合。?
 * public class Product extends BaseEntity<Long> implements TenantAware {
 *     @TableField("tenant_id")
 *     private Long tenantId;
 *     private String productName;
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface TenantAware extends Serializable {

    /**
     * 获取租户 ID
     *
     * @return 租户 ID
     */
    Long getTenantId();

    /**
     * 设置租户 ID
     *
     * @param tenantId 租户 ID
     */
    void setTenantId(Long tenantId);

    /**
     * 判断是否为超级管理员租户
     *
     * @return 超级管理员返。?true，否则返。?false
     */
    default boolean isSuperTenant() {
        Long tid = getTenantId();
        return tid != null && tid.equals(0L);
    }
}
