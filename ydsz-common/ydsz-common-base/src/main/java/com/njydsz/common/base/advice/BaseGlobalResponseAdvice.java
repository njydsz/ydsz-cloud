package com.njydsz.common.base.advice;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.core.response.YdszResponse;

/**
 * PC Web 端全局响应包装
 *
 * <p>继承 {@link AbstractGlobalResponseAdvice}，为 PC Web 场景提供默认响应包装。 对 Controller 返回的字符串类型响应统一封装为 {@link YdszResponse}
 * 标准格式：{@code YdszResponse.success(body)}。
 *
 * <p><b>装配：</b>由 {@code WebMvcConfiguration} 通过 {@code @Bean} + {@code @ConditionalOnMissingBean}
 * 注册，{@code @RestControllerAdvice} 会被 Spring MVC 自动发现为控制器增强。
 *
 * <p><b>执行顺序：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 10， 保证在所有异常处理 Advice 之前包装响应体。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AbstractGlobalResponseAdvice
 * @see YdszResponse
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BaseGlobalResponseAdvice extends AbstractGlobalResponseAdvice {

  /**
   * PC Web 端将原始字符串直接包装为 {@link YdszResponse#success(Object)} 的消息体。
   *
   * @param body Controller 原始返回的字符串
   * @return 包装后的标准响应
   */
  @Override
  protected YdszResponse<String> wrapStringBody(String body) {
    return YdszResponse.success(body);
  }
}
