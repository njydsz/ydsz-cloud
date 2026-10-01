package com.njydsz.generator.handler;

import com.njydsz.common.exception.handler.BaseExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 代码生成器模块全局异常处理器。
 *
 * <p>统一处理代码生成器 REST 接口的异常响应，继承 {@link BaseExceptionHandler} 基类，
 * 由 {@code YdszExceptionHandlerAutoConfiguration} 自动装配 MessageSource/Metrics/Properties。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GeneratorExceptionHandler extends BaseExceptionHandler {

  protected GeneratorExceptionHandler(Environment environment) {
    super(environment);
  }

  @Override
  protected String getLogPrefix() {
    return "【Generator】";
  }
}
