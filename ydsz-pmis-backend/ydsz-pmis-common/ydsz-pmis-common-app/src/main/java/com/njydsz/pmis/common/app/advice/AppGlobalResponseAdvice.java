package com.njydsz.pmis.common.app.advice;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.base.advice.BaseGlobalResponseAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * App 端全局响应包装 Advice
 *
 * <p>继承 {@link BaseGlobalResponseAdvice}，对 Controller 返回的字符串类型响应
 * 进行统一封装为 {@link Result} 标准格式。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AppGlobalResponseAdvice extends BaseGlobalResponseAdvice {

    @Override
    protected Result<String> wrapStringBody(String body) {
        return Result.ok(body);
    }
}
