package com.njydsz.nextwiki.server.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

/**
 * NextWiki 基础设施配置
 * <p>
 * 启用缓存和异步支持，注册外部 HTTP 客户端 Bean。
 * 异步线程池由 ydsz-common-thread 统一管理，通过 YAML 配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableCaching
@EnableAsync
public class AsyncConfig {

    /**
     * RestTemplate Bean（供 LLM 摘要等外部 HTTP 调用使用）
     */
    @Bean
    public RestTemplate nextwikiRestTemplate() {
        return new RestTemplate();
    }
}
