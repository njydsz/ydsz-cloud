package com.njydsz.userinfo.app.openapi;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.app.config.AppOpenApiConfiguration;

/**
 * 用户信息模块 App 端 OpenAPI 配置。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
public class UserInfoAppOpenApiConfiguration extends AppOpenApiConfiguration {

  @Override
  protected String getTitle() {
    return "YDSZ 用户信息 App API 文档";
  }

  @Override
  protected String getDescription() {
    return "<div style='font-size:14px;color:#333;'>YDSZ 用户信息模块 - 移动端 App API 文档</div>";
  }
}
