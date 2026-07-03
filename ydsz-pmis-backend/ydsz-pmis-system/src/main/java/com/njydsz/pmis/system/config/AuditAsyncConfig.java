package com.njydsz.pmis.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 审计模块启用异步执行（@Async 事件监听）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@EnableAsync
public class AuditAsyncConfig {
}
