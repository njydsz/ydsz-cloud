package com.njydsz.common.jdbc.config;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis Mapper 扫描配置。
 *
 * <p>根据 {@link JdbcProperties} 中的 {@code mapperScanPackages} 配置（默认 {@code com.njydsz.**.mapper}），
 *
 * <p>动态注册 {@code MapperScannerConfigurer}，支持多模块独立配置包路径。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@EnableConfigurationProperties(JdbcProperties.class)
@ConditionalOnProperty(prefix = "ydsz.jdbc", name = "enabled", matchIfMissing = true)
public class MapperScanConfiguration {

    /**
     * 注册 Mapper 扫描器
     *
     * @param jdbcProperties JDBC 配置属性
     * @return MapperScannerConfigurer 实例
     */
    @Bean
    @ConditionalOnMissingBean(MapperScannerConfigurer.class)
    public MapperScannerConfigurer mapperScannerConfigurer(JdbcProperties jdbcProperties) {
        MapperScannerConfigurer scannerConfigurer = new MapperScannerConfigurer();
        String basePackages = String.join(",", jdbcProperties.getMapperScanPackages());
        scannerConfigurer.setBasePackage(basePackages);
        return scannerConfigurer;
    }
}
