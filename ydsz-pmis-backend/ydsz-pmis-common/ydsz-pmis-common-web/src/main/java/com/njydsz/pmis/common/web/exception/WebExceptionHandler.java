package com.njydsz.pmis.common.web.exception;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.exception.handler.BaseExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Web 层统一异常处理器
 *
 * <p>继承 {@link BaseExceptionHandler}，提供 Web 端的异常处理逻辑。
 * 核心职责：将各类异常统一映射为 {@link com.njydsz.pmis.common.core.response.BaseResponse} 标准响应格式，
 * 屏蔽内部实现细节，向上游返回友好的错误信息。
 *
 * <p><b>支持的异常类型：</b>
 * <ul>
 *   <li>BusinessException：业务异常</li>
 *   <li>MaxUploadSizeExceededException：文件上传大小超限</li>
 *   <li>IllegalArgumentException：非法参数异常</li>
 *   <li>BindException：参数绑定异常</li>
 *   <li>ConstraintViolationException：约束违反异常</li>
 *   <li>MethodArgumentNotValidException：方法参数校验异常</li>
 *   <li>HttpMessageNotReadableException：请求体解析异常</li>
 *   <li>MissingRequestHeaderException：缺少请求头异常</li>
 *   <li>HttpRequestMethodNotSupportedException：不支持的请求方法</li>
 *   <li>MissingServletRequestParameterException：缺少请求参数</li>
 *   <li>Exception：系统异常（兜底处理）</li>
 * </ul>
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>统一返回 200 HTTP 状态码，业务错误码在响应体中标识</li>
 *   <li>日志记录采用不同级别：error 用于业务异常，warn 用于参数校验异常</li>
 *   <li>参数异常合并多条错误信息为逗号分隔字符串</li>
 * </ul>
 *
 * <p><b>装配：</b>由 {@code com.njydsz.pmis.common.web.config.WebMvcConfiguration} 显式
 * 通过 {@code @Import} 加载，{@code @RestControllerAdvice} 会被 Spring MVC 自动发现为控制器增强。
 * 不要在此类上同时标注 {@code @AutoConfiguration}，避免与 Spring MVC 生命周期冲突。
 *
 * <p><b>执行顺序：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 20，
 * 在 {@code GlobalResponseAdvice} 之后、参数校验 Advice 之前执行。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseExceptionHandler
 * @see BusinessException
 * @see BaseResponse
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class WebExceptionHandler extends BaseExceptionHandler {

    @Override
    protected String getLogPrefix() {
        return "【Web端】";
    }
}