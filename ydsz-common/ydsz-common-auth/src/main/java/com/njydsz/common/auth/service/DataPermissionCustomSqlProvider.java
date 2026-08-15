package com.njydsz.common.auth.service;

import com.njydsz.common.auth.model.DataScopeInfo;

/**
 * 数据权限自定义 SQL 提供者接口。
 *
 * <p>用于在某些复杂业务场景下，通过自定义 SQL 条件来控制数据访问范围。
 * 实现此接口后，可通过 {@link DataScopeInfo#getCustomSqlCondition()} 获取自定义 SQL。
 *
 * <p><b>废弃原因：</b>自定义 SQL 拼接存在 SQL 注入风险，且职责已超出认证鉴权模块边界。
 * 复杂数据权限建议通过数据库视图或独立数据权限服务实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 3.0.0 起标记废弃，计划 4.0.0 移除。
 *             复杂数据权限建议使用数据库视图或独立数据权限服务实现。
 *
 * @see DataScopeInfo
 */
@Deprecated(forRemoval = true, since = "3.0.0")
public interface DataPermissionCustomSqlProvider {

    /**
     * 提供自定义 SQL 条件片段。
     *
     * <p>返回的 SQL 片段会作为 WHERE 条件的一部分拼接到查询 SQL 中。
     * 如果返回 null 或空字符串，则不添加自定义条件。
     *
     * <p><b>SQL 拼接规则：</b>
     * <ul>
     *   <li>如果返回的字符串以 "AND " 或 "OR " 开头，直接拼接</li>
     *   <li>否则，自动以 "AND " 开头拼接</li>
     * </ul>
     *
     * <p><b>安全警告：</b>
     * <ul>
     *   <li>禁止将 HTTP 请求参数直接拼接到返回的 SQL 片段中，以防止 SQL 注入</li>
     *   <li>所有参数必须使用参数化查询（PreparedStatement 占位符）或白名单校验</li>
     *   <li>实现类应仅基于服务端可信数据（如当前用户角色、组织架构）生成 SQL 条件</li>
     * </ul>
     *
     * @param dataScopeInfo 数据权限信息
     * @param tableAlias 当前表的别名（如果有）
     * @return 自定义 SQL 条件片段，如果不需要则返回 null
     */
    String provideCustomSql(DataScopeInfo dataScopeInfo, String tableAlias);

    /**
     * 获取执行顺序。
     *
     * <p>当有多个提供者时，按顺序执行。
     * 数值越小越先执行。
     *
     * @return 执行顺序，默认为最低优先级
     */
    default int getOrder() {
        return Integer.MAX_VALUE;
    }

    /**
     * 判断此提供者是否支持给定的数据权限信息。
     *
     * <p>可用于根据 scope 类型或其他条件过滤提供者。
     *
     * @param dataScopeInfo 数据权限信息
     * @return true-支持，false-不支持
     */
    default boolean supports(DataScopeInfo dataScopeInfo) {
        return dataScopeInfo != null && dataScopeInfo.isCustom();
    }
}
