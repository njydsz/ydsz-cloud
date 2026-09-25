package com.njydsz.userinfo.web.handler;

// JDK
// 第三方
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 本项目
import com.njydsz.common.exception.handler.BaseExceptionHandler;

/**
 * 用户信息模块全局异常处理器，统一处理用户信息相关接口的异常响应。
 *
 * @author ydsz
 * @since 2025/11/06
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserinfoExceptionHandler extends BaseExceptionHandler {

  protected UserinfoExceptionHandler(Environment environment) {
    super(environment);
  }

  @Override
  protected String getLogPrefix() {
    return "【UserInfo】";
  }
}
