package com.njydsz.common.web.advice;

import com.njydsz.common.base.advice.BaseGlobalResponseAdvice;
import com.njydsz.common.core.response.BaseResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Web 端全局响应包装
 *
 * <p>继承 {@link BaseGlobalResponseAdvice}，对 Controller 返回的字符串类型响应 进行统一封装为 {@link BaseResponse} 标准格式。
 *
 * <p><b>触发条件：</b>返回类型为 {@code String} 且未被 {@code @ResponseBody} 注解处理的情况。
 *
 * <p><b>装配：</b>由 {@code WebMvcConfiguration} 通过 {@code @Bean} + {@code @ConditionalOnMissingBean}
 * 注册，{@code @RestControllerAdvice} 会被 Spring MVC 自动发现为控制器增强。
 *
 * <p><b>执行顺序：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 10， 保证在所有异常处理 Advice 之前包装响应体。
 *
 * @author ydsz-team
 * @see BaseGlobalResponseAdvice
 * @see BaseResponse
 * @since 1.0.0
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GlobalResponseAdvice extends BaseGlobalResponseAdvice {

  @Override
  protected BaseResponse<String> wrapStringBody(String body) {
    return BaseResponse.success(body);
  }
}
