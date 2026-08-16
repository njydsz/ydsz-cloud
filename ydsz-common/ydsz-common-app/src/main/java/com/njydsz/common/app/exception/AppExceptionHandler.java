package com.njydsz.common.app.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.app.annotation.AppApi;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.handler.BaseExceptionHandler;

/**
 * 移动端 App 全局异常处理器
 *
 * <p>继承 {@link BaseExceptionHandler}，提供 App 端的异常处理逻辑。 统一处理移动端请求过程中出现的各类异常，将异常信息转换为标准响应格式。
 *
 * <p><b>作用范围限定：</b>本处理器仅对标注了 {@link AppApi} 的控制器生效 （通过 {@code @RestControllerAdvice(annotations =
 * AppApi.class)} 限定）， 避免与 {@code common-web} 模块的异常处理器在同一 Spring 上下文中产生冲突。
 *
 * <p><b>支持的异常类型：</b>
 *
 * <ul>
 *   <li>BusinessException：业务异常
 *   <li>MaxUploadSizeExceededException：文件上传大小超限
 *   <li>IllegalArgumentException：非法参数异常
 *   <li>BindException：数据绑定异常
 *   <li>ConstraintViolationException：约束违反异常
 *   <li>MethodArgumentNotValidException：方法参数校验异常
 *   <li>HttpMessageNotReadableException：请求体解析异常
 *   <li>MissingRequestHeaderException：缺少请求头异常
 *   <li>HttpRequestMethodNotSupportedException：请求方法不支持
 *   <li>MissingServletRequestParameterException：缺少请求参数
 *   <li>Exception：系统异常（兜底处理）
 * </ul>
 *
 * <p><b>装配：</b>由 {@code com.njydsz.common.app.config.AppMvcConfiguration} 显式 通过 {@code @Import}
 * 加载，{@code @RestControllerAdvice} 会被 Spring MVC 自动发现为控制器增强。 不要在此类上同时标注
 * {@code @AutoConfiguration}，避免与 Spring MVC 生命周期冲突。
 *
 * <p><b>执行顺序：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 20， 在 {@code AppGlobalResponseAdvice} 之后、参数校验
 * Advice 之前执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseExceptionHandler
 * @see BusinessException
 * @see AppApi
 */
@Slf4j
@RestControllerAdvice(annotations = AppApi.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AppExceptionHandler extends BaseExceptionHandler {

  /**
   * 构造 App 端异常处理器。
   *
   * @param environment Spring 环境对象
   */
  public AppExceptionHandler(Environment environment) {
    super(environment);
  }

  /**
   * 返回 App 端异常日志前缀
   *
   * <p>用于在多模块日志聚合时区分 App 端与管理端 / Web 端的异常堆栈。
   *
   * @return 日志前缀字符串，默认 {@code "【App端】"}
   */
  @Override
  protected String getLogPrefix() {
    return "【App端】";
  }
}
