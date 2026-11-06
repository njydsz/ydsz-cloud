package com.njydsz.common.exception.openapi;

import java.lang.reflect.Method;
import java.util.Map;

import com.njydsz.common.exception.code.ErrorCodeTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExceptionCodeOpenApiCustomizer} 错误契约示例测试（26.11.06）。
 *
 * <p><b>背景</b>：OpenAPI 文档注入的 {@code application/problem+json} 示例曾手写为
 * {@code {status,title,detail,instance,code}}，与真实响应（RFC 7807 + 平台扩展字段
 * {@code errorCode/traceId/requestId/timestamp/level}）不一致，导致接入方按文档解析失败。
 * 本次优化已修正示例字段，本测试锁定该契约不被回退。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
class ExceptionCodeOpenApiCustomizerTest {

  private ExceptionCodeOpenApiCustomizer customizer;

  @BeforeEach
  void setUp() {
    ErrorCodeTable table = new ErrorCodeTable();
    // 注册真实错误码：PARAM_ERROR → 400，SYSTEM_ERROR → 500
    java.util.Map<String, com.njydsz.common.exception.enums.ExceptionCode> codes =
        new java.util.HashMap<>();
    for (com.njydsz.common.exception.code.CoreExceptionCode code :
        com.njydsz.common.exception.code.CoreExceptionCode.values()) {
      codes.put(code.getCode(), code);
    }
    table.registerAll(codes);
    customizer = new ExceptionCodeOpenApiCustomizer(table, new StaticMessageSource());
  }

  @Test
  @DisplayName("示例 body 含 RFC 7807 基础字段")
  void shouldContainRfc7807Fields() throws Exception {
    String body = invokeBuildExampleBody(400);

    assertThat(body).contains("\"status\"");
    assertThat(body).contains("\"title\"");
    assertThat(body).contains("\"detail\"");
    assertThat(body).contains("\"instance\"");
    assertThat(body).contains("\"type\"");
  }

  @Test
  @DisplayName("示例 body 含平台扩展字段（与真实响应一致）")
  void shouldContainPlatformExtensionFields() throws Exception {
    String body = invokeBuildExampleBody(400);

    assertThat(body).as("历史缺陷：曾缺少 errorCode").contains("\"errorCode\"");
    assertThat(body).as("历史缺陷：曾缺少 traceId").contains("\"traceId\"");
    assertThat(body).as("历史缺陷：曾缺少 requestId").contains("\"requestId\"");
    assertThat(body).as("历史缺陷：曾缺少 timestamp").contains("\"timestamp\"");
    assertThat(body).as("历史缺陷：曾缺少 level").contains("\"level\"");
  }

  @Test
  @DisplayName("示例 body 不含历史错误字段名 code（应为 errorCode）")
  void shouldNotUseLegacyCodeField() throws Exception {
    String body = invokeBuildExampleBody(400);

    assertThat(body).as("历史缺陷：曾用 \"code\" 而非 \"errorCode\"").doesNotContain("\"code\"");
  }

  @Test
  @DisplayName("500 状态码示例使用系统内部错误码")
  void shouldFallbackToInternalErrorFor500() throws Exception {
    String body = invokeBuildExampleBody(500);

    assertThat(body).contains("\"status\":500");
    assertThat(body).contains("\"errorCode\"");
  }

  @Test
  @DisplayName("示例 body 是合法 JSON")
  void shouldProduceValidJson() throws Exception {
    String body = invokeBuildExampleBody(400);

    assertThat(body).startsWith("{").endsWith("}");
    assertThat(body).doesNotContain("${");
  }

  /**
   * 通过反射调用私有方法 {@code buildExampleBody}。
   *
   * <p>该方法为纯函数（无外部依赖），反射调用可避免为测试而放宽可见性。
   *
   * @param status HTTP 状态码
   * @return 示例 body JSON 字符串
   * @throws Exception 反射调用失败
   */
  private String invokeBuildExampleBody(Integer status) throws Exception {
    Method method =
        ExceptionCodeOpenApiCustomizer.class.getDeclaredMethod("buildExampleBody", Integer.class);
    method.setAccessible(true);
    return (String) method.invoke(customizer, status);
  }
}
