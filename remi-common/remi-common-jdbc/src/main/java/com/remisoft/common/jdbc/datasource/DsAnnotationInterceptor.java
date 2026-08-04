package com.remisoft.common.jdbc.datasource;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.remisoft.common.jdbc.annotation.DS;

import lombok.extern.slf4j.Slf4j;

/**
 * @DS 注解拦截器
 *
 * <p>AOP 拦截器，拦截标注了 {@link DS} 的方法或类，动态切换数据源。
 *
 * <p>优先级：方法级注解 > 类级注解 > 默认数据源
 *
 * <p>支持 SpEL 表达式动态解析数据源名称。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class DsAnnotationInterceptor implements MethodInterceptor {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        DS dsAnnotation = resolveDsAnnotation(method);

        if (dsAnnotation == null) {
            return invocation.proceed();
        }

        String dsName = resolveDataSourceName(dsAnnotation.value(), invocation);
        DynamicDataSourceContextHolder.push(dsName);
        log.debug("切换数据源: {} -> {}", method.getName(), dsName);

        try {
            return invocation.proceed();
        } finally {
            String popped = DynamicDataSourceContextHolder.poll();
            log.debug("恢复数据源: {} <- {}", method.getName(), popped);
        }
    }

    /**
     * 解析 @DS 注解（方法级优先）
     */
    private DS resolveDsAnnotation(Method method) {
        DS ds = AnnotationUtils.findAnnotation(method, DS.class);
        if (ds != null) {
            return ds;
        }
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), DS.class);
    }

    /**
     * 解析数据源名称（支持 SpEL）
     */
    private String resolveDataSourceName(String value, MethodInvocation invocation) {
        if (!value.contains("#") && !value.contains("@")) {
            return value;
        }

        try {
            EvaluationContext context = new StandardEvaluationContext();
            Object[] args = invocation.getArguments();
            String[] paramNames = Arrays.stream(invocation.getMethod().getParameters())
                    .map(p -> p.getName())
                    .toArray(String[]::new);

            for (int i = 0; i < args.length && i < paramNames.length; i++) {
                ((StandardEvaluationContext) context).setVariable(paramNames[i], args[i]);
            }

            return parser.parseExpression(value).getValue(context, String.class);
        } catch (Exception e) {
            log.warn("SpEL 解析失败: {}, 使用默认值: {}", value, value, e);
            return value;
        }
    }
}
