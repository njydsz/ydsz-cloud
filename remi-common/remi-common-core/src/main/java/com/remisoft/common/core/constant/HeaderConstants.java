package com.remisoft.common.core.constant;

import com.remisoft.common.core.constant.header.AuthHeaders;
import com.remisoft.common.core.constant.header.ColumnPermissionHeaders;
import com.remisoft.common.core.constant.header.DataScopeHeaders;
import com.remisoft.common.core.constant.header.NetworkHeaders;
import com.remisoft.common.core.constant.header.TraceHeaders;

/**
 * 全局 HTTP 请求头常量定义（向后兼容门面类）。
 *
 * <p><b>迁移指南：</b>新代码不再建议使用此聚合类，而应按职责引用具体头常量类：
 * <ul>
 *   <li>认证/身份：{@link AuthHeaders}</li>
 *   <li>链路追踪：{@link TraceHeaders}</li>
 *   <li>数据权限：{@link DataScopeHeaders}</li>
 *   <li>列级权限：{@link ColumnPermissionHeaders}</li>
 *   <li>网络信息：{@link NetworkHeaders}</li>
 * </ul>
 *
 * <p>约定：
 * <ul>
 *   <li>统一使用 Title Case 风格（如 X-Access-Token）</li>
 *   <li>集合类 header 默认使用 CSV（逗号分隔），也允许多 header 值</li>
 *   <li>表级列规则使用分号分隔不同表（如 {@code table:col1,col2;table2:col3}）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @deprecated 从 1.8.0 起建议使用 {@link AuthHeaders}、{@link TraceHeaders}、{@link DataScopeHeaders}、
 *             {@link ColumnPermissionHeaders}、{@link NetworkHeaders} 等细粒度常量类
 */
@Deprecated(since = "1.8.0", forRemoval = false)
public final class HeaderConstants {

    private HeaderConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // -------------------------------------------------------------------------
    // 认证 / 身份 —— @deprecated 使用 {@link AuthHeaders}
    // -------------------------------------------------------------------------

    /** @deprecated 使用 {@link AuthHeaders#X_ACCESS_TOKEN} */
    @Deprecated
    public static final String X_ACCESS_TOKEN = AuthHeaders.X_ACCESS_TOKEN;

    /** @deprecated 使用 {@link AuthHeaders#X_USER_LANGUAGE} */
    @Deprecated
    public static final String X_USER_LANGUAGE = AuthHeaders.X_USER_LANGUAGE;

    /** @deprecated 使用 {@link AuthHeaders#X_DISTINCT_ID} */
    @Deprecated
    public static final String X_DISTINCT_ID = AuthHeaders.X_DISTINCT_ID;

    /** @deprecated 使用 {@link AuthHeaders#X_IDENTITY_TYPE} */
    @Deprecated
    public static final String X_IDENTITY_TYPE = AuthHeaders.X_IDENTITY_TYPE;

    /** @deprecated 使用 {@link AuthHeaders#X_SERVICE_TYPE} */
    @Deprecated
    public static final String X_SERVICE_TYPE = AuthHeaders.X_SERVICE_TYPE;

    /** @deprecated 使用 {@link AuthHeaders#IDEMPOTENCY_KEY} */
    @Deprecated
    public static final String IDEMPOTENCY_KEY = AuthHeaders.IDEMPOTENCY_KEY;

    // -------------------------------------------------------------------------
    // 数据权限 —— @deprecated 使用 {@link DataScopeHeaders}
    // -------------------------------------------------------------------------

    /** @deprecated 使用 {@link DataScopeHeaders#X_DATA_SCOPE} */
    @Deprecated
    public static final String X_DATA_SCOPE = DataScopeHeaders.X_DATA_SCOPE;

    /** @deprecated 使用 {@link DataScopeHeaders#X_TENANT_ID} */
    @Deprecated
    public static final String X_TENANT_ID = DataScopeHeaders.X_TENANT_ID;

    /** @deprecated 使用 {@link DataScopeHeaders#X_UNIQUE_ID} */
    @Deprecated
    public static final String X_UNIQUE_ID = DataScopeHeaders.X_UNIQUE_ID;

    /** @deprecated 使用 {@link DataScopeHeaders#X_COMPANY_IDS} */
    @Deprecated
    public static final String X_COMPANY_IDS = DataScopeHeaders.X_COMPANY_IDS;

    /** @deprecated 使用 {@link DataScopeHeaders#X_DEPT_IDS} */
    @Deprecated
    public static final String X_DEPT_IDS = DataScopeHeaders.X_DEPT_IDS;

    /** @deprecated 使用 {@link DataScopeHeaders#X_PROJECT_IDS} */
    @Deprecated
    public static final String X_PROJECT_IDS = DataScopeHeaders.X_PROJECT_IDS;

    /** @deprecated 使用 {@link DataScopeHeaders#X_REGION_IDS} */
    @Deprecated
    public static final String X_REGION_IDS = DataScopeHeaders.X_REGION_IDS;

    /** @deprecated 使用 {@link DataScopeHeaders#X_CUSTOM_SQL_CONDITION} */
    @Deprecated
    public static final String X_CUSTOM_SQL_CONDITION = DataScopeHeaders.X_CUSTOM_SQL_CONDITION;

    // -------------------------------------------------------------------------
    // 列级权限 —— @deprecated 使用 {@link ColumnPermissionHeaders}
    // -------------------------------------------------------------------------

    /** @deprecated 使用 {@link ColumnPermissionHeaders#X_VISIBLE_COLUMNS} */
    @Deprecated
    public static final String X_VISIBLE_COLUMNS = ColumnPermissionHeaders.X_VISIBLE_COLUMNS;

    /** @deprecated 使用 {@link ColumnPermissionHeaders#X_EDITABLE_COLUMNS} */
    @Deprecated
    public static final String X_EDITABLE_COLUMNS = ColumnPermissionHeaders.X_EDITABLE_COLUMNS;

    /** @deprecated 使用 {@link ColumnPermissionHeaders#X_COL_PERMISSION_SIGN} */
    @Deprecated
    public static final String X_COL_PERMISSION_SIGN = ColumnPermissionHeaders.X_COL_PERMISSION_SIGN;

    // -------------------------------------------------------------------------
    // 链路追踪 —— @deprecated 使用 {@link TraceHeaders}
    // -------------------------------------------------------------------------

    /** @deprecated 使用 {@link TraceHeaders#TRACE_ID_HEADER} */
    @Deprecated
    public static final String TRACE_ID_HEADER = TraceHeaders.TRACE_ID_HEADER;

    /** @deprecated 使用 {@link TraceHeaders#MDC_TRACE_ID_KEY} */
    @Deprecated
    public static final String MDC_TRACE_ID_KEY = TraceHeaders.MDC_TRACE_ID_KEY;

    /** @deprecated 使用 {@link TraceHeaders#MDC_REQUEST_ID_KEY} */
    @Deprecated
    public static final String MDC_REQUEST_ID_KEY = TraceHeaders.MDC_REQUEST_ID_KEY;

    /** @deprecated 使用 {@link TraceHeaders#W3C_TRACEPARENT} */
    @Deprecated
    public static final String W3C_TRACEPARENT = TraceHeaders.W3C_TRACEPARENT;

    /** @deprecated 使用 {@link TraceHeaders#W3C_TRACESTATE} */
    @Deprecated
    public static final String W3C_TRACESTATE = TraceHeaders.W3C_TRACESTATE;

    // -------------------------------------------------------------------------
    // 网络信息 —— @deprecated 使用 {@link NetworkHeaders}
    // -------------------------------------------------------------------------

    /** @deprecated 使用 {@link NetworkHeaders#X_REQUEST_SOURCE} */
    @Deprecated
    public static final String X_REQUEST_SOURCE = NetworkHeaders.X_REQUEST_SOURCE;

    /** @deprecated 使用 {@link NetworkHeaders#X_FORWARDED_FOR} */
    @Deprecated
    public static final String X_FORWARDED_FOR = NetworkHeaders.X_FORWARDED_FOR;

    // -------------------------------------------------------------------------
    // 新增 —— 仅保留在聚合类中（不推荐对外使用）；新代码请引用具体类
    // -------------------------------------------------------------------------

    /**
     * 租户ID在 SLF4J MDC 中的 key 名称（向后兼容）
     */
    public static final String MDC_TENANT_ID_KEY = "tenantId";

    /**
     * 用户ID在 SLF4J MDC 中的 key 名称（向后兼容）
     */
    public static final String MDC_USER_ID_KEY = "userId";
}
