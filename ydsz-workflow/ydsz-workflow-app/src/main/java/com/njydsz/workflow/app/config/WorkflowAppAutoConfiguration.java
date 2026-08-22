package com.njydsz.workflow.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;

/**
 * 工作流 App 端自动配置。
 *
 * <p>仅在 APP 模式下激活，注册 App 端专属组件。
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范）：</b>双入口架构的 App 端入口层，
 * 通过 @ConditionalOnPlatform 控制激活条件（符合 §34.2.5）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.flow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowAppAutoConfiguration {
}
