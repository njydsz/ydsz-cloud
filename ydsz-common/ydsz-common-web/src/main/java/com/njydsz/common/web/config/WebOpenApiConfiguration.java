package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.base.config.BaseOpenApiConfiguration;

/**
 * Web 端 OpenAPI 配置。
 *
 * <p>继承 {@link BaseOpenApiConfiguration}，为 Web 端 Controller 提供 OpenAPI 分组、
 *
 * <p>安全 Scheme、Tag 描述、JWT 鉴权头。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
public class WebOpenApiConfiguration extends BaseOpenApiConfiguration {

  @Override
  protected String getTitle() {
    return "YDSZ Web API 文档";
  }

  @Override
  protected String getDescription() {
    return "<div style='font-size:14px;color:#333;'>YDSZ 公共框架 - PC Web 端 API 文档</div>";
  }
}
