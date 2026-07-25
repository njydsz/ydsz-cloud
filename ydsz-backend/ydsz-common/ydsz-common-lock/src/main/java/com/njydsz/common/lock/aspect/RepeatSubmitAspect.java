package com.njydsz.common.lock.aspect;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.njydsz.common.exception.custom.DuplicateException;
import com.njydsz.common.lock.annotation.RepeatSubmit;
import com.njydsz.common.lock.idempotent.RepeatSubmitTokenService;

import lombok.extern.slf4j.Slf4j;

/**
 * 表单重复提交防护 AOP 切面
 *
 * <p>拦截标注 {@link RepeatSubmit} 注解的 Controller 方法，基于 Token 令牌模式
 * 防止表单重复提交。
 *
 * <p><b>工作原理：</b>
 * <ol>
 *   <li>前端先调用 {@code GET /repeat-submit/token} 获取一次性 Token</li>
 *   <li>前端提交表单时在请求头携带 {@code X-Repeat-Token}</li>
 *   <li>切面从请求头提取 Token，调用 {@link RepeatSubmitTokenService#validateAndConsume(String)} 校验</li>
 *   <li>校验通过则执行业务方法，失败则抛出 {@link DuplicateException}</li>
 * </ol>
 *
 * <p><b>与 {@link IdempotentAspect} 的区别：</b>
 * <ul>
 *   <li>{@link IdempotentAspect}：基于请求参数摘要的服务端去重，适用于接口幂等性</li>
 *   <li>本切面：基于前端 Token 的防重复提交，适用于表单提交场景</li>
 * </ul>
 *
 * @since 1.0.0
 * @see RepeatSubmit
 * @see RepeatSubmitTokenService
 */
@Slf4j
@Aspect
public class RepeatSubmitAspect {

    private final RepeatSubmitTokenService tokenService;

    public RepeatSubmitAspect(RepeatSubmitTokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * 拦截 {@link RepeatSubmit} 注解方法，执行防重复提交校验
     *
     * @param joinPoint    AOP 连接点
     * @param repeatSubmit 防重复提交注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.warn("[RepeatSubmit] 非 Web 环境，跳过多提交校验");
            return joinPoint.proceed();
        }

        String headerName = repeatSubmit.headerName();
        String token = request.getHeader(headerName);

        if (!StringUtils.hasText(token)) {
            log.warn("[RepeatSubmit] 缺少防重复提交 Token | header={}", headerName);
            throw new DuplicateException("缺少防重复提交 Token，请先获取 Token");
        }

        boolean valid = tokenService.validateAndConsume(token);
        if (!valid) {
            log.warn("[RepeatSubmit] Token 无效或已过期 | header={}, token={}", headerName, token);
            throw new DuplicateException(repeatSubmit.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            log.debug("[RepeatSubmit] 业务方法执行异常 | cause={}", ex.getMessage());
            throw ex;
        }
    }

    /**
     * 获取当前 HTTP 请求
     *
     * @return HttpServletRequest，非 Web 环境返回 null
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
