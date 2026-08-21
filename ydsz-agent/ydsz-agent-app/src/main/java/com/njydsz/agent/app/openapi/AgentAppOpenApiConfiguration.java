package com.njydsz.agent.app.openapi;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.app.config.AppOpenApiConfiguration;

/**
 * Agent 模块 App 端 OpenAPI 配置。
 *
 * <p>继承 {@link AppOpenApiConfiguration}，为 Agent 模块的移动端 API 提供独立的 OpenAPI 分组与文档展示。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
public class AgentAppOpenApiConfiguration extends AppOpenApiConfiguration {

  @Override
  protected String getTitle() {
    return "YDSZ Agent App API 文档";
  }

  @Override
  protected String getDescription() {
    return "<div style='font-size:14px;color:#333;'>YDSZ Agent 模块 - 移动端 App API 文档</div>";
  }
}
