package com.njydsz.pmis.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import org.apache.seata.spring.annotation.GlobalTransactional;

/**
 * Seata 分布式事务自动配置。
 * <p>
 * 当 seata-spring-boot-starter 在 classpath 且 seata.enabled=true 时生效。
 * AT 模式通过 starter 自动装配 DataSourceProxy，无需手动声明 Bean。
 * </p>
 *
 * @author pmis
 */
@Configuration
@ConditionalOnClass(GlobalTransactional.class)
@ConditionalOnProperty(prefix = "seata", name = "enabled", havingValue = "true")
public class SeataAutoConfiguration {
    // AT 模式由 starter 自动装配，此类仅作为配置存在标记
}
