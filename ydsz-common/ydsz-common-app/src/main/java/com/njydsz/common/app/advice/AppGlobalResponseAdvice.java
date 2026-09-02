package com.njydsz.common.app.advice;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.app.annotation.AppApi;
import com.njydsz.common.base.advice.BaseGlobalResponseAdvice;
import com.njydsz.common.core.response.YdszResponse;

/**
 * App 端全局响应包装 Advice
 *
 * <p>继承 {@link BaseGlobalResponseAdvice}，对 Controller 返回的字符串类型响应 进行统一封装为 {@link YdszResponse}
 * 标准格式。与 Web 端的差异在于：
 *
 * <ul>
 *   <li>App 端通常不关心 HTTP 状态码细节，统一使用 {@code YdszResponse.successMsg} 包装
 *   <li>包装后的消息体直接使用原始字符串作为业务消息
 * </ul>
 *
 * <p><b>作用范围限定：</b>本 Advice 仅对标注了 {@link AppApi} 的控制器生效 （通过
 * {@code @RestControllerAdvice(annotations = AppApi.class)} 限定）， 避免与 {@code common-web} 模块的响应包装
 * Advice 在同一 Spring 上下文中产生冲突。
 *
 * <p><b>装配：</b>由 {@code com.njydsz.common.app.config.AppMvcConfiguration} 显式 通过 {@code @Import}
 * 加载，{@code @RestControllerAdvice} 会被 Spring MVC 自动发现为控制器增强。 不要在此类上同时标注
 * {@code @AutoConfiguration}，避免与 Spring MVC 生命周期冲突。
 *
 * <p><b>执行顺序：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 10， 保证在所有异常处理 Advice 之前包装响应体。
 *
 * <p><b>线程安全性：</b>无状态 Bean，线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AppApi
 */
@RestControllerAdvice(annotations = AppApi.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AppGlobalResponseAdvice extends BaseGlobalResponseAdvice {

  /**
   * 将 Controller 返回的字符串响应包装为标准 {@link YdszResponse}
   *
   * <p>App 端约定业务成功时直接使用原始字符串作为业务消息字段， 业务码统一为 {@code SUCCESS}。
   *
   * <p>ydsz-common-core 精简后移除了 {@code successMsg} 静态方法， 此处通过 {@link YdszResponse#success(String,
   * Object)}（msg + data）实现等价语义。
   *
   * @param body Controller 原始返回的字符串
   * @return 包装后的标准响应
   */
  @Override
  protected YdszResponse<String> wrapStringBody(String body) {
    return YdszResponse.success(body, null);
  }
}
