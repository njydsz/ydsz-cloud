package com.njydsz.pmis.common.jdbc.handler;

import java.util.Map;

import net.sf.jsqlparser.expression.Expression;

/**
 * 字段填充处理器接口
 *
 * <p>定义字段自动填充的标准行为，用于在 SQL 执行前自动为指定字段设置值。
 * 主要应用于审计字段（创建人、创建时间、更新人、更新时间）的自动填充。</p>
 *
 * <p>该接口配合 {@link com.njydsz.pmis.common.jdbc.interceptor.FieldFillInterceptor} 使用，
 * 在 INSERT/UPDATE 语句执行前自动注入字段值。</p>
 *
 * <h2>使用示例</h2>
 * <pre>
 * public class MyFieldFillHandler implements FieldFillHandler {
 *     {@literal @}Override
 *     public Expression getFieldFillValue() {
 *         return new StringValue("current_user");
 *     }
 *
 *     {@literal @}Override
 *     public String getFieldFillColumn() {
 *         return "created_by";
 *     }
 *
 *     {@literal @}Override
 *     public boolean createIgnore(String tableName) {
 *         return false;
 *     }
 *
 *     {@literal @}Override
 *     public boolean updateIgnore(String tableName) {
 *         return true;
 *     }
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
public interface FieldFillHandler {

    /**
     * 获取自动填充值表达式
     *
     * <p>返回的表达式将作为 INSERT/UPDATE 语句中对应字段的值。
     * 可以是常量值、函数调用或参数占位符。</p>
     *
     * @return 填充值表达式
     */
    Expression getFieldFillValue();

    /**
     * 获取自动填充的字段名
     *
     * <p>指定需要自动填充的数据库字段名。</p>
     *
     * @return 字段名
     */
    String getFieldFillColumn();

    /**
     * 判断在 INSERT 操作时是否忽略该表的字段填充
     *
     * <p>某些特殊表可能不需要自动填充审计字段，可以通过此方法进行控制。</p>
     *
     * @param tableName 表名
     * @return true 表示忽略（不填充），false 表示需要填充
     */
    default boolean createIgnore(String tableName) {
        return false;
    }

    /**
     * 判断在 UPDATE 操作时是否忽略该表的字段填充
     *
     * <p>某些特殊表可能不需要自动填充审计字段，可以通过此方法进行控制。</p>
     *
     * @param tableName 表名
     * @return true 表示忽略（不填充），false 表示需要填充
     */
    default boolean updateIgnore(String tableName) {
        return false;
    }

    /**
     * 获取字段默认值映射
     *
     * <p>当字段值为 null 时，使用此映射提供的默认值进行填充。
     * 返回的 Map key 为字段名，value 为默认值表达式。</p>
     *
     * @return 默认值映射，返回 null 或空 Map 表示不使用默认值
     */
    default Map<String, Expression> getDefaultValues() {
        return null;
    }

    /**
     * 获取指定字段的默认值
     *
     * <p>当字段值为 null 时调用此方法获取默认值。
     * 优先使用 {@link #getDefaultValues()} 中配置的映射。</p>
     *
     * @param fieldName 字段名
     * @return 默认值表达式，返回 null 表示不应用默认值
     */
    default Expression getDefaultValue(String fieldName) {
        Map<String, Expression> defaults = getDefaultValues();
        if (defaults != null && !defaults.isEmpty()) {
            return defaults.get(fieldName);
        }
        return null;
    }
}