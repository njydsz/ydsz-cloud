package com.remisoft.common.jdbc.enums;

/**
 * 数据源类型枚举，区分主从数据库
 *
 * <p>配合 <b>baomidou dynamic-datasource</b> 使用时，枚举值名称需与配置中的数据源名称一致：
 * <pre>{@code
 * spring:
 *   datasource:
 *     dynamic:
 *       primary: MASTER
 *       datasource:
 *         MASTER:
 *           url: jdbc:mysql://host:3306/db_master
 *         SLAVE:
 *           url: jdbc:mysql://host:3306/db_slave
 * }</pre>
 *
 * <p>使用 {@code @DS} 注解切换数据源：
 * <pre>{@code
 * @DS("SLAVE")
 * public List<User> listUsers() { ... }
 * }</pre>
 *
 * <p><b>注意：</b>dynamic-datasource 为 optional 依赖，需在业务项目中显式引入
 * {@code dynamic-datasource-spring-boot3-starter} 才能启用多数据源功能。
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum DataSourceType {
    /**
     * 主库 - 用于写操作
     */
    MASTER,

    /**
     * 从库 - 用于读操作
     */
    SLAVE
}
