package com.njydsz.literule.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;

import com.njydsz.common.base.config.ConditionalOnPlatform;

/**
 * App 端自动配置聚合（双入口架构）。
 *
 * <p>仅在 {@code ydsz.literule.platform=app} 时激活，聚合 App 端专属组件。
 *
 * <p>通过 {@link ConditionalOnPlatform} 注解控制激活条件，与 Web 端组件互斥。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnPlatform("app")
@AutoConfigureOrder(200)
public class LiteruleAppAutoConfiguration {
  // 聚合配置入口，App 端专属组件通过 @Import 注册
}
