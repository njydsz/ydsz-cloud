package com.njydsz.common.exception.openapi;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.MessageSource;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * SpringDoc OpenAPI 错误码文档自动增强器。
 *
 * <p>扫描项目中所有已注册的异常错误码（{@link ErrorCodeTable}）， 为每个 OpenAPI 操作（operation）的响应自动追加当前接口可能返回的错误码说明，
 * 实现"错误码即文档"的单一信息源。
 *
 * <p><b>启用条件：</b>classpath 中存在 springdoc-openapi 时由 {@link YdszExceptionOpenApiAutoConfiguration}
 * 自动装配；否则本 Bean 不会生效。
 *
 * <p><b>使用方式：</b>默认已启用。可选通过配置关闭：
 *
 * <pre>{@code
 * ydsz.exception.openapi.enabled=false
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ExceptionCodeOpenApiCustomizer implements OpenApiCustomizer {

  private static final String ERROR_CODE_HEADER = "X-Error-Code";
  private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

  private final ErrorCodeTable errorCodeTable;
  private final MessageSource messageSource;

  public ExceptionCodeOpenApiCustomizer(
      ErrorCodeTable errorCodeTable, MessageSource messageSource) {
    this.errorCodeTable = errorCodeTable;
    this.messageSource = messageSource;
  }

  @Override
  public void customise(OpenAPI openApi) {
    if (errorCodeTable == null || errorCodeTable.allCodes().isEmpty()) {
      log.debug("[ExceptionCodeOpenApiCustomizer] 无可注册的错误码，跳过");
      return;
    }

    // 收集所有错误码涉及的 HTTP 状态码
    Set<Integer> httpStatuses = collectHttpStatuses();

    // 为所有 operation 追加通用错误响应
    if (openApi.getPaths() != null) {
      openApi
          .getPaths()
          .forEach(
              (path, pathItem) -> {
                pathItem
                    .readOperations()
                    .forEach(
                        operation -> {
                          ApiResponses responses = operation.getResponses();
                          if (responses == null) {
                            responses = new ApiResponses();
                            operation.setResponses(responses);
                          }
                          appendErrorResponses(responses, httpStatuses);
                        });
              });
    }

    log.info(
        "[ExceptionCodeOpenApiCustomizer] 已为 OpenAPI 文档注入 {} 个 HTTP 状态的错误码响应模型",
        httpStatuses.size());
  }

  /** 收集所有错误码涉及的 HTTP 状态码 */
  private Set<Integer> collectHttpStatuses() {
    Set<Integer> statuses = new LinkedHashSet<>();
    errorCodeTable.allCodes().values().forEach(code -> statuses.add(code.getHttpStatus()));
    return statuses;
  }

  /** 为 ApiResponses 追加各 HTTP 错误状态码的响应模型（含错误码枚举引用） */
  private void appendErrorResponses(ApiResponses responses, Set<Integer> httpStatuses) {
    for (Integer status : httpStatuses) {
      String statusKey = String.valueOf(status);
      if (responses.containsKey(statusKey)) {
        continue;
      }

      Schema errorSchema = buildErrorResponseSchema(status);
      errorSchema.addExample(buildExampleBody(status));
      String description = buildStatusDescription(status);

      ApiResponse response =
          new ApiResponse()
              .description(description)
              .content(
                  new Content()
                      .addMediaType(APPLICATION_PROBLEM_JSON, new MediaType().schema(errorSchema)));

      responses.addApiResponse(statusKey, response);
    }
  }

  /**
   * 构建错误响应 Schema（基于 RFC 9457 ProblemDetail 结构）
   *
   * @param status HTTP 状态码
   * @return 响应 Schema
   */
  private Schema<?> buildErrorResponseSchema(Integer status) {
    Schema<?> schema = new Schema<>();
    schema.setType("object");
    schema.addProperty("status", new Schema<Integer>().type("integer").example(status));
    schema.addProperty("title", new StringSchema().example("错误标题"));
    schema.addProperty("detail", new StringSchema().example("详细错误描述"));
    schema.addProperty("instance", new StringSchema().example("/api/resource"));
    schema.addProperty(ERROR_CODE_HEADER, new StringSchema().example("A00001"));
    return schema;
  }

  /**
   * 构建某 HTTP 状态码下的错误码描述文本
   *
   * @param status HTTP 状态码
   * @return 描述文本
   */
  private String buildStatusDescription(Integer status) {
    StringBuilder sb = new StringBuilder(128);
    sb.append(status).append(" 错误响应（含错误码）\n\n可能的错误码：\n");

    errorCodeTable.allCodes().values().stream()
        .filter(code -> status.equals(code.getHttpStatus()))
        .sorted(Comparator.comparing(ExceptionCode::getCode))
        .forEach(
            code ->
                sb.append("- `")
                    .append(code.getCode())
                    .append("`: ")
                    .append(code.getKey())
                    .append("\n"));
    return sb.toString();
  }

  /**
   * 构建状态码对应的响应示例 body
   *
   * @param status HTTP 状态码
   * @return 示例 body
   */
  private String buildExampleBody(Integer status) {
    return errorCodeTable.allCodes().values().stream()
        .filter(code -> status.equals(code.getHttpStatus()))
        .min(Comparator.comparing(ExceptionCode::getCode))
        .map(
            code ->
                String.format(
                    "{\"status\":%d,\"title\":\"错误\",\"detail\":\"%s\",\"instance\":\"/api/example\",\"code\":\"%s\"}",
                    status, code.getKey().replace(".", " "), code.getCode()))
        .orElse("{\"error\":\"unknown\"}");
  }
}
