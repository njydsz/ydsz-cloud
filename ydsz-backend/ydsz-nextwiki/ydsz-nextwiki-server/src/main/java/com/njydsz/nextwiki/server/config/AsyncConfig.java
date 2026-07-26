package com.njydsz.nextwiki.server.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * NextWiki 基础设施配置
 * <p>
 * 启用缓存和异步支持。RestTemplate 由 ydsz-common-notify 统一提供，
 * 异步线程池由 ydsz-common-thread 统一管理，通过 YAML 配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableCaching
@EnableAsync
public class AsyncConfig {
}
