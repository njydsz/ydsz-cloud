package com.njydsz.common.tenant.lifecycle;

/**
 * 租户状态枚举。
 *
 * <p>对应 {@code ydsz_tenant} 表的 {@code status} 字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum TenantStatus {

    /** 活跃（正常使用） */
    ACTIVE,

    /** 暂停（拒绝该租户所有请求，用于欠费/违规等场景） */
    SUSPENDED,

    /** 已下线（租户主动退出，数据保留但不接受请求） */
    OFFLINE,

    /** 已删除（逻辑删除，数据保留但不接受任何请求） */
    DELETED
}
