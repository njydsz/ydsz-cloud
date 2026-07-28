package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import nl.basjes.parse.useragent.UserAgentAnalyzer;

/**
 * User-Agent 解析器配置。
 *
 * <p>注册 {@code UserAgentAnalyzer} Bean，基于 Yauaa 库解析浏览器、操作系统、设备类型、爬虫标识。
 *
 * <p>供日志、限流、UI 适配等场景识别客户端环境。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@AutoConfiguration
@ConditionalOnClass(name = "nl.basjes.parse.useragent.UserAgentAnalyzer")
@ConditionalOnProperty(prefix = "ydsz.web.user-agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserAgentConfiguration {

    @Bean
    public UserAgentAnalyzer userAgentAnalyzer() {
        return UserAgentAnalyzer
                .newBuilder()
                .hideMatcherLoadStats()
                .withCache(10000)
                .build();
    }
}
