package com.njydsz.message.web.openapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.message.domain.vo.ErrorCodeVO;
import com.njydsz.message.server.service.error.MessageExceptionCodeRegistry;

/**
 * SpringDoc OpenAPI customizer that injects message module error codes metadata as {@code x-error-codes}
 * extension into the generated OpenAPI document.
 *
 * <p>OpenAPI snippet (top-level extensions):
 *
 * <pre>{@code
 * {
 *   "openapi": "3.0.3",
 *   "extensions": {
 *     "x-error-codes": [
 *       { "code": "B91001", "messageKey": "message.template.not.found", "httpStatus": 404, "description": "Template not found" }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p><b>Disable:</b> {@code ydsz.message.error-codes.openapi=false}
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see MessageExceptionCodeRegistry
 * @see ErrorCodeVO
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springdoc.core.customizers.OpenApiCustomizer")
@ConditionalOnProperty(
    prefix = "ydsz.message.error-codes",
    name = "openapi",
    havingValue = "true",
    matchIfMissing = true)
public class MessageExceptionCodeOpenApiCustomizer {

  /** OpenAPI extension key for error codes metadata */
  private static final String EXTENSION_KEY_ERROR_CODES = "x-error-codes";

  /** message module error code registry */
  private final MessageExceptionCodeRegistry errorCodeRegistry;

  /**
   * Registers the error code OpenAPI customizer bean.
   *
   * <p>SpringDoc auto-detects all {@link OpenApiCustomizer} beans and applies them during document generation.
   *
   * @return the customizer bean
   */
  @Bean
  @ConditionalOnMissingBean(name = "messageExceptionCodeOpenApiCustomizer")
  public OpenApiCustomizer messageExceptionCodeOpenApiCustomizer() {
    return this::customiseOpenApi;
  }

  /**
   * Injects message module error codes metadata into the OpenAPI top-level extensions.
   *
   * @param openApi the OpenAPI document being generated
   */
  private void customiseOpenApi(OpenAPI openApi) {
    if (openApi == null) {
      return;
    }

    List<ErrorCodeVO> errorCodes = errorCodeRegistry.listAll();

    Map<String, Object> extensions = openApi.getExtensions();
    if (extensions == null) {
      extensions = new LinkedHashMap<>(4);
      openApi.setExtensions(extensions);
    }

    extensions.put(EXTENSION_KEY_ERROR_CODES, errorCodes);

    log.info(
        "[MessageExceptionCodeOpenApiCustomizer] injected {} error codes into x-error-codes extension",
        errorCodes.size());
  }
}
