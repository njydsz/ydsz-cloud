package com.njydsz.system.app.openapi;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.app.config.AppOpenApiConfiguration;

/**
 * 系统管理模块 App 端 OpenAPI 配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
public class SystemAppOpenApiConfiguration extends AppOpenApiConfiguration {

  @Override
  protected String getTitle() {
    return "YDSZ 系统管理 App API 文档";
  }

  @Override
  protected String getDescription() {
    return "<div style='font-size:14px;color:#333;'>YDSZ 系统管理模块 - 移动端 App API 文档</div>";
  }
}
