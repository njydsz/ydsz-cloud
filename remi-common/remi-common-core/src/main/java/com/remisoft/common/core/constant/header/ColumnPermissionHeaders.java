package com.remisoft.common.core.constant.header;

/**
 * 列级权限（字段级）相关 HTTP 请求头常量
 *
 * <p>定义可见列、可编辑列以及对应的 HMAC 校验签名 header。
 *
 * <p>SQL 拦截器通过读取这些 header 动态改写 SELECT/INSERT/UPDATE 语句，
 * 实现对不同用户精细化到列级别的权限控制。
 *
 * <p>对应模块：remi-common-jdbc（SQL 拦截器读取改写）
 *
 * @author remi-team
 * @since 1.8.0
 */
public final class ColumnPermissionHeaders {

    private ColumnPermissionHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 列级权限：表级可见列规则
     *
     * <p>控制 SELECT 查询中哪些列对当前用户可见。
     *
     * <p>格式：{@code table:col1,col2;table2:col3,col4}
     * <ul>
     *   <li>分号 {@code ;} 分隔不同表</li>
     *   <li>冒号 {@code :} 分隔表名和列名</li>
     *   <li>逗号 {@code ,} 分隔同表多列</li>
     *   <li>表名和列名均小写比对</li>
     * </ul>
     *
     * <p>SQL 拦截器行为：
     * <ul>
     *   <li>当 SELECT 包含 {@code *} 或 {@code t.*} 时，替换为允许列清单</li>
     *   <li>当 SELECT 明确列出列时，仅保留允许的列</li>
     *   <li>若规则为空或不包含某表，表示全部可见（不过滤）</li>
     * </ul>
     *
     * <p>示例：{@code sys_user:id,name,email;sys_role:id,role_name}
     */
    public static final String X_VISIBLE_COLUMNS = "X-Visible-Columns";

    /**
     * 列级权限：表级可编辑列规则
     *
     * <p>控制 INSERT/UPDATE 操作中哪些列对当前用户可写。
     *
     * <p>格式：同 {@link #X_VISIBLE_COLUMNS}
     *
     * <p>SQL 拦截器行为：
     * <ul>
     *   <li>INSERT/UPDATE 时过滤掉不可编辑的列</li>
     *   <li>若某表没有任何可编辑列，抛出异常阻断写入</li>
     *   <li>若规则为空或不包含某表，表示全部可编辑（不过滤）</li>
     * </ul>
     *
     * <p>示例：{@code sys_user:name,email,phone;sys_role:role_name,description}
     */
    public static final String X_EDITABLE_COLUMNS = "X-Editable-Columns";

    /**
     * 列级权限：签名值
     *
     * <p>用于对列权限数据（X-Visible-Columns / X-Editable-Columns）进行 HMAC-SHA256 签名校验，
     * 防止攻击者伪造或篡改列权限 Header。
     *
     * <p>签名算法：HMAC-SHA256(visibleColumns + "|" + editableColumns, appSecret)
     *
     * <p>服务端收到请求后，会使用相同的 AppSecret 重新计算签名并与此 Header 值对比，
     * 签名不匹配时将拒绝请求并记录安全审计日志。
     */
    public static final String X_COL_PERMISSION_SIGN = "X-Col-Permission-Sign";
}
