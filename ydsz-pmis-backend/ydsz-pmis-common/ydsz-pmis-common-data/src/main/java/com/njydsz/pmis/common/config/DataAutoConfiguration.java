package com.njydsz.pmis.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 数据层自动配置
 *
 * <p>聚合 data 模块所有配置类，通过 Spring Boot 3 自动装配机制注册。
 * 引入 {@code ydsz-pmis-common-data} 依赖后自动生效。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link MybatisPlusAutoConfiguration} - MyBatis-Plus 拦截器 + 雪花 ID</li>
 *   <li>{@link PmisCacheConfig} - 多级缓存配置</li>
 *   <li>{@link MultiLevelCacheConfig} - Caffeine + Redis 二级缓存</li>
 *   <li>{@link BloomFilterConfig} - 布隆过滤器（防缓存穿透）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Configuration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor")
@Import({
    MybatisPlusAutoConfiguration.class,
    PmisCacheConfig.class,
    MultiLevelCacheConfig.class,
    BloomFilterConfig.class
})
public class DataAutoConfiguration {
}
