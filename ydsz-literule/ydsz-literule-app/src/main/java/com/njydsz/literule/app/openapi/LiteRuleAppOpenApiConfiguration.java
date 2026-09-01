package com.njydsz.literule.app.openapi;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.app.config.AppOpenApiConfiguration;

/**
 * 规则引擎模块 App 端 OpenAPI 配置。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
public class LiteRuleAppOpenApiConfiguration extends AppOpenApiConfiguration {

  @Override
  protected String getTitle() {
    return "YDSZ 规则引擎 App API 文档";
  }

  @Override
  protected String getDescription() {
    return "<div style='font-size:14px;color:#333;'>YDSZ 规则引擎模块 - 移动端 App API 文档</div>";
  }
}
