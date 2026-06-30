package com.njydsz.pmis.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 公共模块自动配置
 *
 * <p>供其他微服务通过 @SpringBootApplication(scanBasePackages) 或 @ComponentScan 引入
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@ComponentScan("com.njydsz.pmis.common")
public class CommonAutoConfiguration {
}
