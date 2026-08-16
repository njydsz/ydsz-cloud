package com.njydsz.common.web.version;

import java.lang.reflect.Method;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import lombok.extern.slf4j.Slf4j;

/**
 * 支持 API 版本路由的 RequestMappingHandlerMapping
 *
 * <p>自定义 HandlerMapping，在构建 RequestMappingInfo 时注入 {@link ApiVersionCondition}， 实现基于版本的接口路由。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

  private final ApiVersionProperties properties;

  public ApiVersionRequestMappingHandlerMapping(ApiVersionProperties properties) {
    this.properties = properties;
  }

  @Override
  protected RequestCondition<?> getCustomMethodCondition(Method method) {
    ApiVersion apiVersion = AnnotatedElementUtils.findMergedAnnotation(method, ApiVersion.class);
    if (apiVersion != null) {
      log.debug("发现 @ApiVersion 注解 | method={}, version={}", method.getName(), apiVersion.value());
      return new ApiVersionCondition(apiVersion.value(), properties);
    }
    return null;
  }

  @Override
  protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
    ApiVersion apiVersion =
        AnnotatedElementUtils.findMergedAnnotation(handlerType, ApiVersion.class);
    if (apiVersion != null) {
      log.debug(
          "发现类级 @ApiVersion 注解 | class={}, version={}",
          handlerType.getSimpleName(),
          apiVersion.value());
      return new ApiVersionCondition(apiVersion.value(), properties);
    }
    return null;
  }
}
