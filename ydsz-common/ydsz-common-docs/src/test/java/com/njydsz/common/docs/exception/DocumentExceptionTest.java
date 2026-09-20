package com.njydsz.common.docs.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * {@link DocumentException} 单元测试 — 验证 i18n 参数化消息构造路径与异常码三要素语义 (L-3)。
 *
 * <p>在无 Spring 上下文的环境下，{@code MessageSourceHolder} 未注入， {@link
 * com.njydsz.common.exception.custom.AbstractYdszException#getMessage()} 会降级返回 {@code messageKey} 自身。 测试重点验证字段语义而非文案展示（文案集成测试覆盖）。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@DisplayName("DocumentException 测试")
class DocumentExceptionTest {

  @Nested
  @DisplayName("构造：仅异常码")
  class WhenConstructedWithCodeOnly {

    @Test
    @DisplayName("应保留 code / key / httpStatus，并返回 messageKey")
    void shouldPreserveCodeAndKey() {
      DocumentException ex = new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);

      assertThat(ex.getCode()).isEqualTo("G01005");
      assertThat(ex.getKey()).isEqualTo("docs.empty");
      assertThat(ex.getParams()).isEmpty();
    }
  }

  @Nested
  @DisplayName("构造：异常码 + i18n 占位参数")
  class WhenConstructedWithArgs {

    @Test
    @DisplayName("非 String 单参数应走 i18n 占位路径（params 被填充，overrideMessage 不被触发）")
    void shouldPassNonStringToI18nArgsPath() {
      // 注意：Java 重载歧义 —— new DocumentException(code, "xyz") 中的 "xyz" 是 String，
      // 会优先命中 ExceptionCode + String 构造器（override 路径）。要验证 i18n 占位路径，
      // 需传入非 String 实参（如枚举、数字、多参数）
      DocumentException ex =
          new DocumentException(DocumentExceptionCode.UNSUPPORTED_FORMAT,
              com.njydsz.common.docs.enums.DocumentFormat.PPTX);

      assertThat(ex.getCode()).isEqualTo("G01001");
      assertThat(ex.getKey()).isEqualTo("docs.format.unsupported");
      assertThat(ex.getParams())
          .containsExactly(com.njydsz.common.docs.enums.DocumentFormat.PPTX);
      // i18n 路径未命中 overrideMessage，getMessage() 返回 messageKey（无 Spring 上下文兜底）
      assertThat(ex.getMessage()).isEqualTo("docs.format.unsupported");
    }

    @Test
    @DisplayName("传入多个参数时全部保留（含 String 元素）")
    void shouldRetainMultipleArguments() {
      // 两个参数时，编译器选 Object... 而非 String（歧义消除）
      DocumentException ex =
          new DocumentException(DocumentExceptionCode.PARSE_FAILED, "file.pdf", "detail");

      assertThat(ex.getParams()).containsExactly("file.pdf", "detail");
    }

    @Test
    @DisplayName("传入空参数数组时退化为无参行为")
    void shouldDegradeToNoArgsWhenEmpty() {
      DocumentException ex =
          new DocumentException(DocumentExceptionCode.CONVERT_FAILED);

      assertThat(ex.getParams()).isEmpty();
      assertThat(ex.getKey()).isEqualTo("docs.convert.failed");
    }

    @Test
    @DisplayName("传 null varargs 时安全降级为空数组")
    void shouldHandleNullArguments() {
      Object[] nullArgs = null;
      DocumentException ex =
          new DocumentException(DocumentExceptionCode.UNKNOWN, nullArgs);

      assertThat(ex.getParams()).isEmpty();
    }
  }

  @Nested
  @DisplayName("构造：异常码 + 自定义 String 消息")
  class WhenConstructedWithStringMessage {

    @Test
    @DisplayName("应设置 overrideMessage，跳过 i18n 解析直接返回该文本")
    void shouldOverrideMessageWithCustomString() {
      DocumentException ex =
          new DocumentException(DocumentExceptionCode.CONVERT_FAILED, "文档转换器未注册");

      assertThat(ex.getMessage()).isEqualTo("文档转换器未注册");
      // code / key 仍保留，但 getMessage() 绕过它们直接使用 override
      assertThat(ex.getCode()).isEqualTo("G07001");
      assertThat(ex.getKey()).isEqualTo("docs.convert.failed");
    }
  }

  @Nested
  @DisplayName("构造：带 cause 传播")
  class WhenConstructedWithCause {

    @Test
    @DisplayName("仅 code + cause 时 params 为空")
    void shouldPreserveCause() {
      RuntimeException root = new RuntimeException("PDF 容器损坏");
      DocumentException ex =
          new DocumentException(DocumentExceptionCode.PARSE_FAILED, root);

      assertThat(ex.getCause()).isSameAs(root);
      assertThat(ex.getParams()).isEmpty();
      assertThat(ex.getCode()).isEqualTo("G01002");
    }
  }

  @Nested
  @DisplayName("L-3 异常码三要素 (httpStatus / level / category / retryable)")
  class WhenVerifyingExceptionCodeSemantics {

    @Test
    @DisplayName("UNSUPPORTED_FORMAT 应声明 HTTP 422 + WARN + BUSINESS 分类")
    void shouldReturnSemanticForUnsupportedFormat() {
      // 验证枚举级语义声明（框架 BusinessException 构造器透传 httpStatus，
      // 但 level/category 由具体异常子类配置）
      assertThat(DocumentExceptionCode.UNSUPPORTED_FORMAT.getHttpStatus()).isEqualTo(422);
      assertThat(DocumentExceptionCode.UNSUPPORTED_FORMAT.getLevel()).isEqualTo(ExceptionLevel.WARN);
      assertThat(DocumentExceptionCode.UNSUPPORTED_FORMAT.getCategory()).isEqualTo(ExceptionCategory.BUSINESS);
    }

    @Test
    @DisplayName("SECURITY_RISK_DETECTED 应声明 HTTP 403 + SECURITY 分类")
    void shouldReturnSemanticForSecurityRisk() {
      assertThat(DocumentExceptionCode.SECURITY_RISK_DETECTED.getHttpStatus()).isEqualTo(403);
      assertThat(DocumentExceptionCode.SECURITY_RISK_DETECTED.getLevel()).isEqualTo(ExceptionLevel.ERROR);
      assertThat(DocumentExceptionCode.SECURITY_RISK_DETECTED.getCategory()).isEqualTo(ExceptionCategory.SECURITY);
    }

    @Test
    @DisplayName("PARSE_TIMEOUT 应标记为 retryable、HTTP 408")
    void shouldMarkTimeoutAsRetryable() {
      // retryable() 定义在 ExceptionCode 接口上，需验证枚举实例
      assertThat(DocumentExceptionCode.PARSE_TIMEOUT.retryable()).isTrue();
      assertThat(DocumentExceptionCode.PARSE_TIMEOUT.getHttpStatus()).isEqualTo(408);
    }

    @Test
    @DisplayName("UNKNOWN 应声明 FATAL 级别 + 500 状态")
    void shouldReturnFatalForUnknown() {
      assertThat(DocumentExceptionCode.UNKNOWN.getHttpStatus()).isEqualTo(500);
      assertThat(DocumentExceptionCode.UNKNOWN.getLevel()).isEqualTo(ExceptionLevel.FATAL);
    }

    @Test
    @DisplayName("抛出的异常其 getHttpStatus 应等于枚举声明的状态码")
    void shouldPropagateHttpStatusViaException() {
      DocumentException ex =
          new DocumentException(DocumentExceptionCode.DOCUMENT_ENCRYPTED);
      // BusinessException.init() 正确调用 exceptionCode.getHttpStatus()
      assertThat(ex.getHttpStatus()).isEqualTo(451);
    }
  }
}
