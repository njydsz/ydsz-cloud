package com.njydsz.userinfo.app.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.userinfo.app.config.ConditionalOnPlatform;

/**
 * App 端 OpenAPI 配置（P1-2 双入口架构）。
 *
 * <p>仅在 {@code ydsz.userinfo.platform=app} 时激活，为移动端/应用端 API 提供 Swagger 文档。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
@ConditionalOnPlatform("app")
public class AppOpenApiConfiguration {

  /**
   * 配置 App 端 OpenAPI 元数据。
   *
   * @return OpenAPI Bean
   */
  @Bean
  public OpenAPI appOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("用户信息中心 - App API")
            .description("移动端/应用端接口文档（用户认证、社交登录、个人资料管理）")
            .version("26.09.01")
            .contact(new Contact()
                .name("ydsz-team")
                .email("team@ydsz.com"))
            .license(new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0")));
  }
}
