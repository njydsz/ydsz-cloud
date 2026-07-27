package com.njydsz.common.tenant;

/**
 * 租户维度枚举。
 *
 * <p>MULTI 模式下支持多级租户，每个维度对应一个数据库列和上下文值。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum TenantDimension {

    /** 主租户维度 */
    TENANT,

    /** 集团维度 */
    GROUP,

    /** 公司维度 */
    COMPANY
}
