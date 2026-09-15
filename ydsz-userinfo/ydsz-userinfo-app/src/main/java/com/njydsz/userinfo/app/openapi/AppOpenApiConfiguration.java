package com.njydsz.userinfo.app.openapi;

import org.springframework.context.annotation.Configuration;

import com.njydsz.common.base.config.BaseOpenApiConfiguration;
import com.njydsz.userinfo.app.config.ConditionalOnPlatform;

/**
 * App 端 OpenAPI 配置（P1-2 双入口架构）。
 *
 * <p>仅在 {@code ydsz.userinfo.platform=app} 时激活，为移动端/应用端 API 提供 Swagger 文档。
 *
 * <p><b>复用说明（ADR-009 公共能力收敛）：</b>OpenAPI 文档构建（公共请求头注册、JWT Bearer
 * 安全方案、联系信息、外部文档链接）全部复用 ydsz-common-base {@link BaseOpenApiConfiguration}，
 * 本类仅提供业务文档标题与描述。此前本类自建 {@code OpenAPI} Bean（硬编码 Info/Contact/License），
 * 与 common-app {@code AppOpenApiConfiguration} 重复建设，现已收敛（同名类 E1 命中一并消除）。
 *
 * <p><b>文档开关：</b>文档暴露由框架开关 {@code ydsz.doc.enabled=true} 统一控制，与 common-web /
 * common-app 保持一致；未开启时本配置不注册任何 Bean（此前为无条件下暴露 Swagger，属越权暴露面）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.15 改为继承 common-base {@link BaseOpenApiConfiguration}，移除自建 OpenAPI Bean
 */
@Configuration
@ConditionalOnPlatform("app")
public class AppOpenApiConfiguration extends BaseOpenApiConfiguration {

  /**
   * App 端 API 文档标题。
   *
   * @return 文档标题
   */
  @Override
  protected String getTitle() {
    return "用户信息中心 - App API";
  }

  /**
   * App 端 API 文档描述。
   *
   * @return 文档描述
   */
  @Override
  protected String getDescription() {
    return "移动端/应用端接口文档（用户认证、社交登录、个人资料管理）";
  }
}
