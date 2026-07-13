package com.njydsz.pmis.common.jdbc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.annotation.DS;

/**
 * 动态数据源配置类
 *
 * <p>基于 MyBatis-Plus 的 dynamic-datasource 组件，提供多数据源/读写分离支持。
 * 通过 {@code @DS} 注解在类或方法级别指定数据源，无需手动切换。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 1. application.yml 配置多数据源
 * spring:
 *   datasource:
 *     dynamic:
 *       primary: master
 *       strict: false
 *       datasource:
 *         master:
 *           url: jdbc:mysql://localhost:3306/master_db
 *           username: root
 *           password: 123456
 *         slave:
 *           url: jdbc:mysql://localhost:3306/slave_db
 *           username: root
 *           password: 123456
 *
 * // 2. 在 Service 上使用 @DS 注解
 * @Service
 * @DS("slave")
 * public class UserService {
 *
 *     public User getUser(Long id) {
 *         // 使用 slave 数据源
 *     }
 *
 *     @DS("master")
 *     public void saveUser(User user) {
 *         // 使用 master 数据源
 *     }
 * }
 * }</pre>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>本配置仅在引入了 {@code dynamic-datasource-spring-boot3-starter} 依赖后生效</li>
 *   <li>需要配合 {@code spring.datasource.dynamic} 配置使用</li>
 *   <li>与 JDBC 模块原有的单数据源配置互斥，启用动态数据源后请移除 {@code spring.datasource.url} 等配置</li>
 *   <li>方法级别 {@code @DS} 优先级高于类级别</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see DS
 */
@AutoConfiguration
@ConditionalOnClass(DynamicRoutingDataSource.class)
@ConditionalOnProperty(prefix = "spring.datasource.dynamic", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DynamicDataSourceConfiguration {
    // 本配置类仅作为自动装配入口，实际功能由 dynamic-datasource 自动配置完成。
    // @DS 注解由 dynamic-datasource 的 AOP 自动处理，无需额外配置。
}
