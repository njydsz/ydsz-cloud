package com.njydsz.pmis.common.jdbc.domain;

import java.util.HashSet;
import java.util.Set;

import com.njydsz.pmis.common.jdbc.enums.InterceptTableStrategy;

import lombok.Data;
/**
 * SQL 拦截器配置类
 *
 * <p>定义 SQL 拦截器的基本配置参数，包括拦截策略、启用状态、目标表列表和字段名。</p>
 *
 * <h2>配置说明</h2>
 * <ul>
 *   <li>interceptTableStrategy：表拦截策略（INCLUDE/EXCLUDE）</li>
 *   <li>enabled：是否启用拦截</li>
 *   <li>tables：需要拦截或排除的表集合</li>
 *   <li>column：目标字段名</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see InterceptTableStrategy
 */
@Data
public class InterceptConfig {

    /**
     * 表拦截策略
     *
     * <p>默认值为 EXCLUDE（排除模式），即不处理配置列表中的表。</p>
     *
     * @see InterceptTableStrategy
     */
    private InterceptTableStrategy interceptTableStrategy = InterceptTableStrategy.EXCLUDE;

    /**
     * 是否启用拦截
     *
     * <p>默认值为 false，需要设置为 true 才生效。</p>
     */
    private Boolean enabled = false;

    /**
     * 目标表集合
     *
     * <p>配合 interceptTableStrategy 使用：
     * <ul>
     *   <li>EXCLUDE 模式：表在此集合中时不进行拦截</li>
     *   <li>INCLUDE 模式：仅对在此集合中的表进行拦截</li>
     * </ul>
     */
    private Set<String> tables = new HashSet<>();

    /**
     * 目标字段名
     *
     * <p>指定需要进行填充或其他处理的数据库字段名。</p>
     */
    private String column = "";
}