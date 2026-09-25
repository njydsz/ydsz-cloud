package com.njydsz.workflow.web.handler;

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
 * 工作流模块全局异常处理器，统一处理工作流相关接口的异常响应。
 *
 * @author ydsz
 * @since 2025/11/06
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowExceptionHandler extends BaseExceptionHandler {

  protected WorkflowExceptionHandler(Environment environment) {
    super(environment);
  }

  @Override
  protected String getLogPrefix() {
    return "【Workflow】";
  }
}
