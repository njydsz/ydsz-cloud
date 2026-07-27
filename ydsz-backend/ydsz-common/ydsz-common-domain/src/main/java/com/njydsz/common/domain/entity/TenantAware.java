package com.njydsz.common.domain.entity;

import java.io.Serializable;

/**
 * 租户维度标记接口。
 *
 * <p>标识该实体支持多租户数据隔离。配合 SQL 拦截器自动注入 tenant_id 条件。
 *
 * <p><b>注意：</b>租户 ID 类型为 {@code String}，与 DDL {@code VARCHAR(20)} 和
 * SQL 拦截器 {@code StringValue} 一致。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class Product extends BaseEntity<Long> implements TenantAware {
 *     // tenantId 已在 MpBaseEntity 中声明，无需重复
 *     private String productName;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TenantAware extends Serializable {

    /**
     * 获取租户 ID。
     *
     * @return 租户 ID
     */
    String getTenantId();

    /**
     * 设置租户 ID。
     *
     * @param tenantId 租户 ID
     */
    void setTenantId(String tenantId);
}
