package com.remisoft.common.jdbc.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.remisoft.common.jdbc.interceptor.LogicalDeleteInterceptor;

import lombok.Data;

/**
 * 逻辑删除拦截器配置类
 *
 * <p>用于配置自定义逻辑删除拦截器的行为参数，
 * 包括删除标记字段名、已删除/正常状态的值等。
 *
 * <p>配置示例：
 * <pre>
 * # application.yml
 * remi:
 *   jdbc:
 *     logical-delete:
 *       enable: true                       # 是否启用逻辑删除拦截
 *       deleted-column: deleted           # 删除标记字段名
 *       deleted-value: 1                   # 已删除记录的值
 *       normal-value: 0                    # 正常记录的值
 * </pre>
 *
 * <p>与 MyBatis-Plus @TableLogic 注解的对比：
 * <ul>
 *   <li>@TableLogic：自动将 DELETE 转为 UPDATE，SELECT 追加条件</li>
 *   <li>本配置：仅追加 WHERE 条件，需要业务层配合实现真正的软删除</li>
 * </ul>
 *
 * <p>使用建议：
 * <ul>
 *   <li>启用本拦截器后，建议在业务层提供 removeById 方法，手动执行 UPDATE deleted = 1</li>
 *   <li>对于需要查询已删除记录的场景，提供单独的方法并手动排除条件</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see LogicalDeleteInterceptor
 */
@Data
@ConfigurationProperties(prefix = "remi.jdbc.logical-delete")
public class LogicalDeleteConfiguration {

    /**
     * 是否启用逻辑删除拦截
     * <p>默认为 false，需要手动设置为 true 以启用
     */
    private boolean enable = false;

    /**
     * 删除标记字段名
     * <p>用于标识数据库表中的逻辑删除标记列名
     * <p>默认值：deleted
     */
    private String deletedColumn = "deleted";

    /**
     * 已删除记录的标记值
     * <p>当记录被删除时，deleted 字段的值
     * <p>默认值：1
     */
    private Long deletedValue = 1L;

    /**
     * 正常记录的标记值
     * <p>正常记录（未删除）时，deleted 字段的值
     * <p>默认值：0
     */
    private Long normalValue = 0L;

    /**
     * 忽略逻辑删除拦截的表列表（忽略大小写）。
     * <p>例如系统配置表、字典表等不含有 deleted 列的表。
     */
    private Set<String> ignoreTables = new HashSet<>();

    /**
     * 获取规范化后的忽略表集合（小写化）。
     *
     * @return 小写化的忽略表集合
     */
    public Set<String> getNormalizedIgnoreTables() {
        if (ignoreTables == null || ignoreTables.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>(ignoreTables.size());
        for (String table : ignoreTables) {
            if (table != null) {
                normalized.add(table.trim().toLowerCase());
            }
        }
        return normalized;
    }
}