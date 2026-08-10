package com.njydsz.common.jdbc.interceptor;

import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.core.constant.DataScopeConstants;
import com.njydsz.common.jdbc.config.DataPermissionConfiguration;
import com.njydsz.common.jdbc.permission.DataPermissionContext;
import com.njydsz.common.jdbc.permission.DataPermissionContextResolver;
import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 行级数据权限拦截器
 *
 * <p>基于 MyBatis-Plus {@link InnerInterceptor} 接口实现，继承 {@link JsqlParserSupport}
 * 解析和改写 SQL，在 SELECT/UPDATE/DELETE 语句中自动追加行级过滤条件。</p>
 *
 * <p>支持以下权限维度：
 * <ul>
 *   <li>USER：用户级隔离（userColumn）</li>
 *   <li>GROUP：公司级隔离（companyColumn）</li>
 *   <li>COMPANY/DEPT：部门级隔离（deptColumn）</li>
 *   <li>PROJECT：项目级隔离（projectColumn）</li>
 *   <li>REGION：区域级隔离（regionColumn）</li>
 * </ul>
 *
 * <p><b>注意：</b>租户隔离（TENANT 维度）已由独立的 {@code TenantIsolationInterceptor}
 * （common-tenant 模块）处理，本拦截器不再负责 tenant_id 条件注入。
 *
 * <p>技术要点：
 * <ul>
 *   <li>JOIN 表行级条件追加到 JOIN ON 条件，避免 LEFT JOIN 被改写为 INNER JOIN</li>
 *   <li>当原条件为 OR 时，追加 AND 条件自动加括号保持语义正确性</li>
 *   <li>支持 SetOperationList（UNION/UNION ALL）复杂查询</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RowPermissionInnerInterceptor extends JsqlParserSupport implements InnerInterceptor {

    /** 数据权限配置 */
    private final DataPermissionConfiguration config;
    /** 数据权限上下文解析器 */
    private final DataPermissionContextResolver contextResolver;
    /** 标准化后的表名集合（小写），与拦截策略配合使用 */
    private final Set<String> normalizedTables;

    /** 安全值正则模式，仅允许字母、数字及常见安全字符 */
    private static final Pattern SAFE_VALUE_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-.:,@]+$");

    /** 永假表达式 {@code 1=0}，用于 fail-closed 时拒绝所有数据 */
    private static final Expression DENY_ALL_EXPRESSION = new EqualsTo(new LongValue(1L), new LongValue(0L));

    /**
     * 构造行级数据权限拦截器
     *
     * @param config          数据权限配置
     * @param contextResolver 数据权限上下文解析器
     */
    public RowPermissionInnerInterceptor(DataPermissionConfiguration config,
                                         DataPermissionContextResolver contextResolver) {
        this.config = config;
        this.contextResolver = contextResolver;
        this.normalizedTables = normalizeTableSet(config);
    }

    /**
     * 应用行级权限到 SQL（供复合拦截器调用）
     *
     * @param sql     原始 SQL 语句
     * @param context 数据权限上下文，为 null 时使用空上下文
     * @return 追加行级过滤条件后的 SQL 语句
     */
    public String apply(String sql, DataPermissionContext context) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return sql;
        }
        if (context == null) {
            context = DataPermissionContext.empty();
        }
        return parserSingle(sql, context);
    }

    /**
     * 标准化表名集合，去除空白、去除前后空格并统一为小写
     *
     * @param config 数据权限配置
     * @return 标准化后的表名集合，配置为空时返回空集合
     */
    private Set<String> normalizeTableSet(DataPermissionConfiguration config) {
        return DataPermissionHelper.normalizeTableSet(config);
    }

    /**
     * SQL 执行前回调，解析当前 SQL 类型并追加行级权限过滤条件
     *
     * @param sh                 StatementHandler
     * @param connection         数据库连接
     * @param transactionTimeout 事务超时时间
     */
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        SqlCommandType sct = ms.getSqlCommandType();
        if (!isSupportedSqlType(sct)) {
            return;
        }
        // 检查 @DataPermissionIgnore 注解，跳过数据权限拦截
        if (isDataPermissionIgnored(ms)) {
            return;
        }
        DataPermissionContext context = contextResolver.resolve();
        PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
        mpBs.sql(parserSingle(mpBs.sql(), context));
    }

    /**
     * 检查 MappedStatement 对应的方法是否标注了 @DataPermissionIgnore 注解
     *
     * @param ms MyBatis MappedStatement
     * @return 标注了忽略注解时返回 true，否则返回 false
     */
    private boolean isDataPermissionIgnored(MappedStatement ms) {
        return DataPermissionHelper.isDataPermissionIgnored(ms);
    }

    /**
     * 判断当前 SQL 命令类型是否支持行级权限拦截
     *
     * @param sct SQL 命令类型
     * @return 支持 SELECT/UPDATE/DELETE 时返回 true，否则返回 false
     */
    private boolean isSupportedSqlType(SqlCommandType sct) {
        return sct == SqlCommandType.SELECT
                || sct == SqlCommandType.UPDATE
                || sct == SqlCommandType.DELETE;
    }

    /**
     * 处理 SELECT 语句，追加行级权限过滤条件
     *
     * @param select SELECT 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    数据权限上下文
     */
    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        DataPermissionContext context = (DataPermissionContext) obj;
        processSelectBody(select, context);
    }

    /**
     * 处理 DELETE 语句，追加行级权限过滤条件
     *
     * @param delete DELETE 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    数据权限上下文
     */
    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        DataPermissionContext context = (DataPermissionContext) obj;
        Table table = delete.getTable();
        if (!shouldApply(table)) {
            return;
        }
        delete.setWhere(mergeWhere(delete.getWhere(), buildRowScopeOrFail(table, context)));
    }

    /**
     * 处理 UPDATE 语句，追加行级权限过滤条件
     *
     * @param update UPDATE 语句对象
     * @param index  语句索引
     * @param sql    原始 SQL 字符串
     * @param obj    数据权限上下文
     */
    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        DataPermissionContext context = (DataPermissionContext) obj;
        Table table = update.getTable();
        if (!shouldApply(table)) {
            return;
        }
        update.setWhere(mergeWhere(update.getWhere(), buildRowScopeOrFail(table, context)));
    }

    /**
     * 递归处理 SELECT 语句体，对 PlainSelect 追加行级权限，对 SetOperationList 递归处理每个子查询
     *
     * @param select  SELECT 语句对象
     * @param context 数据权限上下文
     */
    private void processSelectBody(Select select, DataPermissionContext context) {
        if (select == null) {
            return;
        }
        if (select instanceof PlainSelect) {
            PlainSelect plain = (PlainSelect) select;
            applyRowScopeToFromItem(plain, context);
            applyRowScopeToJoins(plain, context);
            return;
        }
        if (select instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) select;
            if (CollectionUtils.isNotEmpty(setOperationList.getSelects())) {
                setOperationList.getSelects().forEach(it -> processSelectBody(it, context));
            }
        }
    }

    /**
     * 对 PlainSelect 的主表（FROM）追加行级权限过滤条件，若 FROM 为子查询则递归处理
     *
     * @param plain   PlainSelect 语句
     * @param context 数据权限上下文
     */
    private void applyRowScopeToFromItem(PlainSelect plain, DataPermissionContext context) {
        FromItem fromItem = plain.getFromItem();
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            if (shouldApply(table)) {
                plain.setWhere(mergeCondition(plain.getWhere(), buildRowScopeOrFail(table, context)));
            }
            return;
        }
        if (fromItem instanceof ParenthesedSelect) {
            ParenthesedSelect parenthesedSelect = (ParenthesedSelect) fromItem;
            processSelectBody(parenthesedSelect.getSelect(), context);
        }
    }

    /**
     * 对 PlainSelect 的 JOIN 表追加行级权限过滤条件到 ON 子句，避免 LEFT JOIN 被改写为 INNER JOIN
     *
     * @param plain   PlainSelect 语句
     * @param context 数据权限上下文
     */
    private void applyRowScopeToJoins(PlainSelect plain, DataPermissionContext context) {
        if (CollectionUtils.isEmpty(plain.getJoins())) {
            return;
        }
        for (Join join : plain.getJoins()) {
            if (join.getRightItem() instanceof Table) {
                Table table = (Table) join.getRightItem();
                if (shouldApply(table)) {
                    Expression existingOn = JSqlParserHelper.getJoinOnExpression(join);
                    Expression newOn = mergeCondition(existingOn, buildRowScopeOrFail(table, context));
                    JSqlParserHelper.setJoinOnExpression(join, newOn);
                }
                continue;
            }
            if (join.getRightItem() instanceof ParenthesedSelect) {
                ParenthesedSelect parenthesedSelect = (ParenthesedSelect) join.getRightItem();
                processSelectBody(parenthesedSelect.getSelect(), context);
            }
        }
    }

    /**
     * 根据数据权限上下文构建行级过滤表达式，支持按数据范围维度生成对应条件。
     *
     * <p><b>fail-closed 原则：</b>当上下文为空、值不安全或无法构建任何权限条件时，
     * 返回永假表达式 {@code 1=0} 拒绝所有数据，避免数据过曝。
     *
     * @param table   目标表
     * @param context 数据权限上下文
     * @return 行级过滤表达式；无法构建条件时返回 {@code 1=0} 永假表达式
     */
    private Expression buildRowScopeOrFail(Table table, DataPermissionContext context) {
        Expression scope = buildRowScope(table, context);
        if (scope == null) {
            // fail-closed：无法确定权限上下文时拒绝所有数据，避免数据泄露
            log.warn("无法为表 {} 构建行级权限条件，已追加 1=0 拒绝数据访问。context={}",
                table != null ? table.getName() : null, context);
            return DENY_ALL_EXPRESSION;
        }
        return scope;
    }

    /**
     * 根据数据权限上下文构建行级过滤表达式，支持按数据范围维度生成对应条件
     *
     * @param table   目标表
     * @param context 数据权限上下文
     * @return 行级过滤表达式，无权限条件时返回 null
     */
    private Expression buildRowScope(Table table, DataPermissionContext context) {
        if (context == null || context.isEmptyRowScope()) {
            return null;
        }
        Expression out = null;
        String scope = context.getDataScope();
        if (scope == null) {
            out = and(out, equals(table, config.getUserColumn(), context.getUserId()));
            out = and(out, in(table, config.getCompanyColumn(), context.getCompanyIds()));
            out = and(out, in(table, config.getDeptColumn(), context.getDeptIds()));
            out = and(out, in(table, config.getProjectColumn(), context.getProjectIds()));
            out = and(out, in(table, config.getRegionColumn(), context.getRegionIds()));
            return out;
        }

        if (DataScopeConstants.USER.equals(scope)) {
            return equals(table, config.getUserColumn(), context.getUserId());
        }
        if (DataScopeConstants.PROJECT.equals(scope)) {
            return in(table, config.getProjectColumn(), context.getProjectIds());
        }
        if (DataScopeConstants.REGION.equals(scope)) {
            return in(table, config.getRegionColumn(), context.getRegionIds());
        }
        if (DataScopeConstants.GROUP.equals(scope)) {
            return in(table, config.getCompanyColumn(), context.getCompanyIds());
        }
        if (DataScopeConstants.COMPANY.equals(scope) || DataScopeConstants.DEPT.equals(scope)) {
            return in(table, config.getDeptColumn(), context.getDeptIds());
        }
        // default: apply all conditions
        out = and(out, equals(table, config.getUserColumn(), context.getUserId()));
        out = and(out, in(table, config.getCompanyColumn(), context.getCompanyIds()));
        out = and(out, in(table, config.getDeptColumn(), context.getDeptIds()));
        out = and(out, in(table, config.getProjectColumn(), context.getProjectIds()));
        out = and(out, in(table, config.getRegionColumn(), context.getRegionIds()));
        return out;
    }

    /**
     * 合并 WHERE 条件，将追加条件与原 WHERE 条件通过 AND 连接
     *
     * @param oldWhere 原 WHERE 条件
     * @param append   待追加的条件
     * @return 合并后的 WHERE 条件，追加条件为 null 时返回原条件
     */
    private Expression mergeWhere(Expression oldWhere, Expression append) {
        if (append == null) {
            return oldWhere;
        }
        if (oldWhere == null) {
            return append;
        }
        return mergeCondition(oldWhere, append);
    }

    /**
     * 合并两个表达式，通过 AND 连接
     *
     * @param oldExpression 原表达式
     * @param append        待追加的表达式
     * @return 合并后的表达式，追加表达式为 null 时返回原表达式
     */
    private Expression mergeCondition(Expression oldExpression, Expression append) {
        if (append == null) {
            return oldExpression;
        }
        if (oldExpression == null) {
            return append;
        }
        return new AndExpression(oldExpression, append);
    }

    /**
     * 将两个表达式通过 AND 连接，任一为 null 时返回非 null 的表达式
     *
     * @param left  左表达式
     * @param right 右表达式
     * @return AND 连接后的表达式，两侧均为 null 时返回 null
     */
    private Expression and(Expression left, Expression right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }
        return new AndExpression(left, right);
    }

    /**
     * 构建等值条件表达式（table.columnName = 'value'），值不安全时返回 null
     *
     * @param table      目标表
     * @param columnName 列名
     * @param value      比较值
     * @return 等值条件表达式，列名或值为空、值不安全时返回 null
     */
    private Expression equals(Table table, String columnName, String value) {
        if (StringUtils.isBlank(columnName) || StringUtils.isBlank(value)) {
            return null;
        }
        if (!isSafeValue(value)) {
            return null;
        }
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(table, columnName));
        eq.setRightExpression(new StringValue(value));
        return eq;
    }

    /**
     * 校验值是否为安全值，仅允许字母、数字及常见安全字符
     *
     * @param value 待校验的值
     * @return 安全时返回 true，否则返回 false
     */
    private boolean isSafeValue(String value) {
        return SAFE_VALUE_PATTERN.matcher(value).matches();
    }

    /**
     * 构建 IN 条件表达式（table.columnName IN ('v1','v2',...)），过滤不安全的值
     *
     * @param table      目标表
     * @param columnName 列名
     * @param values     IN 条件值集合
     * @return IN 条件表达式，列名为空、值集合为空或无安全值时返回 null
     */
    private Expression in(Table table, String columnName, Set<String> values) {
        if (StringUtils.isBlank(columnName) || CollectionUtils.isEmpty(values)) {
            return null;
        }
        List<StringValue> safeValues = values.stream()
                .filter(this::isSafeValue)
                .map(StringValue::new)
                .collect(Collectors.toList());
        if (safeValues.isEmpty()) {
            return null;
        }
        InExpression in = new InExpression();
        in.setLeftExpression(new Column(table, columnName));
        in.setRightExpression(new ExpressionList<>(safeValues));
        return in;
    }

    /**
     * 判断是否应对指定表应用行级权限拦截，根据拦截策略（INCLUDE/EXCLUDE）和标准化表名集合判断
     *
     * @param table 目标表
     * @return 需要拦截时返回 true，否则返回 false
     */
    private boolean shouldApply(Table table) {
        return DataPermissionHelper.shouldApply(table, config, normalizedTables);
    }

}
