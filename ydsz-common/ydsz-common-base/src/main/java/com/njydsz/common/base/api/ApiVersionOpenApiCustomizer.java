package com.njydsz.common.base.api;

import java.lang.reflect.Method;
import java.util.Optional;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * API 版本 OpenAPI 自定义处理器。
 *
 * <p>自动读取 Controller 方法上的 {@link ApiVersion} 注解，将版本信息注入到 OpenAPI 文档中：
 *
 * <ul>
 *   <li>在操作描述前追加版本徽章（如 {@code [v1]}）
 *   <li>废弃接口追加废弃警告说明
 *   <li>添加 {@code X-Api-Version} 响应头到 200 响应
 * </ul>
 *
 * <p><b>装配方式：</b>由 {@code OpenApiAutoConfiguration} 注册为 Spring Bean，当 classpath 中存在 springdoc-openapi 时激活。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ApiVersionOpenApiCustomizer implements OperationCustomizer {

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    Method method = handlerMethod.getMethod();
    Optional<ApiVersion> apiVersion = ApiVersionResolver.resolve(method);

    if (apiVersion.isPresent()) {
      ApiVersion version = apiVersion.get();

      // 在 summary 前追加版本标识
      String originalSummary = operation.getSummary();
      if (originalSummary != null) {
        String versionTag = "[" + version.value() + "]";
        if (!originalSummary.startsWith(versionTag)) {
          operation.setSummary(versionTag + " " + originalSummary);
        }
      }

      // 废弃接口追加说明
      if (version.deprecated()) {
        String deprecationNotice = buildDeprecationNotice(version);
        String originalDesc = operation.getDescription();
        if (originalDesc != null) {
          operation.setDescription(originalDesc + "\n\n" + deprecationNotice);
        } else {
          operation.setDescription(deprecationNotice);
        }

        // 标记为 deprecated
        operation.setDeprecated(true);
      }

      // 给所有响应追加 X-Api-Version 响应头
      // 注意：Header 挂在 ApiResponse 上（addHeaderObject），ApiResponses 容器本身无 header 方法
      if (operation.getResponses() != null) {
        Header apiVersionHeader =
            new Header().description("API 版本号: " + version.value()).required(true);
        operation
            .getResponses()
            .values()
            .forEach(apiResponse -> apiResponse.addHeaderObject("X-Api-Version", apiVersionHeader));
      }
    }

    return operation;
  }

  /** 构建废弃说明文本 */
  private String buildDeprecationNotice(ApiVersion version) {
    StringBuilder sb = new StringBuilder();
    sb.append("> ⚠️ **已废弃 (Deprecated)**\n>\n");
    sb.append("> 此接口已废弃，不建议在新项目中使用。");

    if (!version.replacement().isBlank()) {
      sb.append("\n>\n> **替代接口：** `").append(version.replacement()).append("`");
    }

    if (!version.removal().isBlank()) {
      sb.append("\n>\n> **计划移除版本：** `").append(version.removal()).append("`");
    }

    return sb.toString();
  }
}
