package com.njydsz.pmis.common.jdbc.config;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis Mapper 扫描配置类
 *
 * <p>根据 {@link JdbcProperties} 中的 mapperScanPackages 配置，
 * 动态注册 MapperScannerConfigurer，默认为 {@code com.njydsz.pmis.**.mapper}。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
    public MapperScannerConfigurer mapperScannerConfigurer(JdbcProperties jdbcProperties) {
        MapperScannerConfigurer scannerConfigurer = new MapperScannerConfigurer();
        String basePackages = String.join(",", jdbcProperties.getMapperScanPackages());
        scannerConfigurer.setBasePackage(basePackages);
        return scannerConfigurer;
    }
}
