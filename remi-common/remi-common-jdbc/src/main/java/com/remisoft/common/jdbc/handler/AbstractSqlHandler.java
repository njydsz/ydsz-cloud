package com.remisoft.common.jdbc.handler;

import java.util.HashSet;
import java.util.Set;

import com.remisoft.common.jdbc.config.InterceptConfig;
import com.remisoft.common.jdbc.enums.InterceptTableStrategy;
import com.remisoft.common.util.string.StringUtils;

/**
 * SQL 拦截器抽象基类
 *
 * <p>提供 SQL 拦截处理的基础实现，封装通用的表过滤策略和列名处理逻辑。
 * 子类只需关注具体的 SQL 转换实现，无需重复编写表过滤逻辑。</p>
 *
 * <h2>表拦截策略</h2>
 * <ul>
 *   <li>EXCLUDE（排除模式）：处理除指定表之外的所有表</li>
 *   <li>INCLUDE（包含模式）：仅处理指定表</li>
 * </ul>
 *
 * <h2>子类实现要求</h2>
 * <pre>
 * public class MyInterceptor extends AbstractSqlHandler {
 *     {@literal @}Override
 *     protected boolean customIgnore() {
 *         return false;
 *     }
 *
 *     {@literal @}Override
 *     protected String getDefaultColumn() {
 *         return "my_column";
 *     }
 * }
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public abstract class AbstractSqlHandler {

    /**
     * 拦截配置
     */
    protected InterceptConfig interceptConfig;

    public AbstractSqlHandler(InterceptConfig interceptConfig) {
        this.interceptConfig = interceptConfig;
    }

    /**
     * 默认的表忽略策略实现
     *
     * <p>根据配置的 {@link InterceptTableStrategy} 策略判断是否需要忽略指定表：
     * <ul>
     *   <li>EXCLUDE 模式：表在配置列表中则忽略</li>
     *   <li>INCLUDE 模式：表不在配置列表中则忽略</li>
     * </ul>
     *
     * @param tableName        表名
     * @param interceptConfig  拦截配置
     * @return true 表示忽略该表，false 表示需要处理
     */
    protected boolean defaultIgnoreStrategy(String tableName, InterceptConfig interceptConfig) {
        if (customIgnore()) {
            return true;
        }
        if (tableName == null) {
            return true;
        }

        InterceptTableStrategy interceptTableStrategy = interceptConfig.getInterceptTableStrategy();
        Set<String> tables = interceptConfig.getTables();
        String normalizedTableName = tableName.trim().toLowerCase();

        // 标准化配置表集合为小写，确保大小写不敏感匹配
        Set<String> normalizedTables = new HashSet<>(tables.size());
        for (String table : tables) {
            if (table != null) {
                normalizedTables.add(table.trim().toLowerCase());
            }
        }

        if (InterceptTableStrategy.EXCLUDE.equals(interceptTableStrategy)) {
            return normalizedTables.contains(normalizedTableName);
        } else if (InterceptTableStrategy.INCLUDE.equals(interceptTableStrategy)) {
            return !normalizedTables.contains(normalizedTableName);
        } else {
            throw new IllegalStateException("未指定表拦截策略");
        }
    }

    /**
     * 处理列名配置
     *
     * <p>优先使用配置中指定的列名，如果未配置则使用子类提供的默认列名。</p>
     *
     * @param column 配置的列名
     * @return 最终使用的列名
     */
    protected String handleColumn(String column) {
        if (StringUtils.isNotBlank(column)) {
            return column;
        }

        column = getDefaultColumn();
        if (StringUtils.isNotBlank(column)) {
            return column;
        }

        throw new IllegalStateException(this.getClass() + "未指定填充字段,请检查");
    }

    /**
     * 获取默认列名
     *
     * <p>子类实现此方法提供默认的列名。</p>
     *
     * @return 默认列名
     */
    protected String getDefaultColumn() {
        return "";
    }

    /**
     * 自定义忽略策略
     *
     * <p>子类可覆盖此方法实现自定义的表忽略逻辑。</p>
     *
     * @return true 表示忽略，false 表示不忽略
     */
    protected abstract boolean customIgnore();
}