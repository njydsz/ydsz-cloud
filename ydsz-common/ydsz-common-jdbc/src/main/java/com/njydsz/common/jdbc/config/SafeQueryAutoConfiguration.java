package com.njydsz.common.jdbc.config;

import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.njydsz.common.domain.config.DomainProperties;
import com.njydsz.common.jdbc.interceptor.SafeQueryInnerInterceptor;

import lombok.extern.slf4j.Slf4j;

/**
 * 安全查询拦截器自动配置（ORDER BY 注入防护 + 深度分页检测）。
 *
 * <p>向 {@link MybatisPlusInterceptor} 注入 {@link SafeQueryInnerInterceptor}，
 * 统一处理：
 * <ul>
 *   <li>ORDER BY 字段安全校验（正则 + 可选白名单）</li>
 *   <li>深度分页检测与拦截</li>
 * </ul>
 *
 * <p>配置项：
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     safe-query:
 *       enabled: true                # 启用安全查询拦截（默认 true）
 *       strict-mode: false           # true=拒绝非法排序字段抛异常, false=忽略
 *       order-by-whitelist: []       # 排序字段白名单
 * }</pre>
 *
 * <p>触发条件：
 * <ul>
 *   <li>{@code ydsz.jdbc.safe-query.enabled=true}（默认启用）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.7.0
 * @see SafeQueryInnerInterceptor
 * @see MybatisPlusConfiguration
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnProperty(prefix = "ydsz.jdbc.safe-query", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({JdbcProperties.class, DomainProperties.class})
public class SafeQueryAutoConfiguration {

    /**
     * 创建安全查询拦截器 Bean。
     *
     * @param domainProperties 领域配置（深度分页阈值）
     * @param jdbcProperties JDBC 配置（安全查询相关配置）
     * @return SafeQueryInnerInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(SafeQueryInnerInterceptor.class)
    public SafeQueryInnerInterceptor safeQueryInnerInterceptor(
            ObjectProvider<DomainProperties> domainProperties,
            ObjectProvider<JdbcProperties> jdbcProperties) {

        SafeQueryInnerInterceptor interceptor = new SafeQueryInnerInterceptor(
                domainProperties.getIfAvailable());

        // 从 JDBC 配置加载安全查询设置
        JdbcProperties jdbc = jdbcProperties.getIfAvailable();
        if (jdbc != null && jdbc.getSafeQuery() != null) {
            JdbcProperties.SafeQuery safeQuery = jdbc.getSafeQuery();
            interceptor.setEnabled(safeQuery.isEnabled());
            interceptor.setStrictMode(safeQuery.isStrictMode());
            if (safeQuery.getOrderByWhitelist() != null) {
                interceptor.setOrderByWhitelist(safeQuery.getOrderByWhitelist());
            }
        }

        log.info("安全查询拦截器已启用 (strictMode={})", interceptor.isStrictMode());
        return interceptor;
    }

    /**
     * 将安全查询拦截器注册到 MyBatis-Plus 拦截器链。
     *
     * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链
     * @param safeQueryInterceptor  SafeQueryInnerInterceptor 实例
     */
    @Bean
    public Object safeQueryInterceptorRegistration(MybatisPlusInterceptor mybatisPlusInterceptor,
                                                    SafeQueryInnerInterceptor safeQueryInterceptor) {
        // 查找是否已存在
        for (InnerInterceptor inner : mybatisPlusInterceptor.getInterceptors()) {
            if (inner instanceof SafeQueryInnerInterceptor) {
                return inner; // 已存在，跳过注册
            }
        }
        // 注册到拦截器链（放在靠后位置，让其他拦截器先处理）
        mybatisPlusInterceptor.addInnerInterceptor(safeQueryInterceptor);
        return safeQueryInterceptor;
    }
}
