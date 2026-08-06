package com.remisoft.common.auth.context;

import com.remisoft.common.tenant.spi.TenantContextHolder;

/**
 * 租户上下文持有者实现
 *
 * <p>委托给 {@link AuthContextUtils} 获取租户 ID
 *
 * @author remi-team
 * @since 1.0.0
 */
public class TenantContextHolderImpl implements TenantContextHolder {

    @Override
    public String getTenantId() {
        return AuthContextUtils.getTenantId();
    }
}
