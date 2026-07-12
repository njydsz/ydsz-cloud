package com.njydsz.pmis.common.constant;

/**
 * 全局 HTTP 请求头常量定义。
 *
 * <p>约定统一使用 Title Case 风格（如 X-Access-Token）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class HeaderConstants {

    private HeaderConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 访问令牌 */
    public static final String X_ACCESS_TOKEN = "X-Access-Token";
    /** 刷新令牌 */
    public static final String X_REFRESH_TOKEN = "X-Refresh-Token";
    /** 用户ID */
    public static final String X_USER_ID = "X-User-Id";
    /** 用户语言 */
    public static final String X_USER_LANGUAGE = "X-User-Language";
    /** 租户ID */
    public static final String X_TENANT_ID = "X-Tenant-Id";
    /** 身份类型 */
    public static final String X_IDENTITY_TYPE = "X-Identity-Type";
    /** 服务类型 */
    public static final String X_SERVICE_TYPE = "X-Service-Type";
    /** 数据范围 */
    public static final String X_DATA_SCOPE = "X-Data-Scope";
    /** 公司ID集合 */
    public static final String X_COMPANY_IDS = "X-Company-Ids";
    /** 部门ID集合 */
    public static final String X_DEPT_IDS = "X-Dept-Ids";
    /** 项目ID集合 */
    public static final String X_PROJECT_IDS = "X-Project-Ids";
    /** 区域ID集合 */
    public static final String X_REGION_IDS = "X-Region-Ids";
    /** 可见列规则 */
    public static final String X_VISIBLE_COLUMNS = "X-Visible-Columns";
    /** 可编辑列规则 */
    public static final String X_EDITABLE_COLUMNS = "X-Editable-Columns";
    /** 链路追踪ID */
    public static final String X_TRACE_ID = "X-Trace-Id";
}
