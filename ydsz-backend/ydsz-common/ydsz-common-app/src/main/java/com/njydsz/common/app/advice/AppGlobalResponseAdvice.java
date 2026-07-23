package com.njydsz.common.app.advice;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.base.advice.BaseGlobalResponseAdvice;
import com.njydsz.common.core.response.BaseResponse;

/**
 * App 端全局响应包装 Advice
 *
 * <p>继承 {@link BaseGlobalResponseAdvice}，对 Controller 返回的字符串类型响应
 * 进行统一封装为 {@link BaseResponse} 标准格式。与 Web 端的差异在于：
 * <ul>
 *   <li>App 端通常不关心 HTTP 状态码细节，统一使用 {@code BaseResponse.successMsg} 包装</li>
 *   <li>包装后的消息体直接使用原始字符串作为业务消息</li>
 * </ul>
 *
 * <p><b>装配：</b>由 {@code com.njydsz.common.app.config.AppMvcConfiguration} 显式
 * 通过 {@code @Import} 加载，{@code @RestControllerAdvice} 会被 Spring MVC 自动发现为控制器增强。
 * 不要在此类上同时标注 {@code @AutoConfiguration}，避免与 Spring MVC 生命周期冲突。
 *
 * <p><b>执行顺序：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 10，
 * 保证在所有异常处理 Advice 之前包装响应体。
 *
 * <p><b>线程安全性：</b>无状态 Bean，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AppGlobalResponseAdvice extends BaseGlobalResponseAdvice {

    /**
     * 将 Controller 返回的字符串响应包装为标准 {@link BaseResponse}
     *
     * <p>App 端约定业务成功时直接使用原始字符串作为业务消息字段，
     * 业务码统一为 {@code SUCCESS}。
     *
     * @param body Controller 原始返回的字符串
     * @return 包装后的标准响应
     */
    @Override
    protected BaseResponse<String> wrapStringBody(String body) {
        return BaseResponse.successMsg(body);
    }
}
