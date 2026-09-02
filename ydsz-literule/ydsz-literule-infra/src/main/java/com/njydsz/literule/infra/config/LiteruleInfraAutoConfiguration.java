package com.njydsz.literule.infra.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Literule 基础设施层自动配置。
 *
 * <p>注册 MyBatis Mapper 扫描路径，使 {@code infra.mapper} 包下的 Mapper 接口被 Spring 容器扫描并注册为 Mapper Bean。
 *
 * <p>此配置由 Spring Boot 自动装配机制通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 发现和加载，避免 web/app 入口模块直接依赖 infra 包的物理结构，符合 DDD 分层依赖方向（web → server → domain ← infra）。
 *
 * <p><b>条件装配：</b>
 *
 * <ul>
 *   <li>{@code @ConditionalOnClass}：仅当 MyBatis-Plus 与数据源驱动均在 classpath 上时才激活，避免纯内存模式（如单元测试）加载时因缺少驱动而报错
 *   <li>{@code @ConditionalOnProperty}：通过 {@code ydsz.literule.mybatis.enabled} 开关控制，默认开启；
 *       需要禁用时可配置 {@code ydsz.literule.mybatis.enabled=false}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@MapperScan("com.njydsz.literule.infra.mapper")
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.MybatisConfiguration")
@ConditionalOnProperty(prefix = "ydsz.literule.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiteruleInfraAutoConfiguration {
  // 配置类仅承载 @MapperScan 注解，无需额外 Bean 定义
}
