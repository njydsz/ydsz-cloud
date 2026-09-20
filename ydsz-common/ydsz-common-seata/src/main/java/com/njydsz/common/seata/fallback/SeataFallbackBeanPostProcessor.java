package com.njydsz.common.seata.fallback;

import com.njydsz.common.seata.annotation.SeataFallbackMode;
import com.njydsz.common.seata.annotation.YdszGlobalTransactional;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/**
 * Seata 事务降级拦截器 — 为标注 {@link YdszGlobalTransactional} 的方法提供降级能力。
 *
 * <p>当 Seata Server 不可用（网络分区、TC 选举、Server 宕机）导致全局事务无法开启时，
 * 根据 {@link YdszGlobalTransactional#fallbackMode()} 配置的策略进行处理：
 * <ul>
 *   <li>{@link SeataFallbackMode#FAIL} — 继续抛出异常（默认行为）</li>
 *   <li>{@link SeataFallbackMode#LOCAL_TRANSACTION} — 跳过全局事务，直接执行目标方法（最终一致性）</li>
 *   <li>{@link SeataFallbackMode#SKIP} — 跳过并记录 WARN 日志</li>
 * </ul>
 *
 * <p>实现原理：通过 {@link SmartInstantiationAwareBeanPostProcessor} 在 Bean 初始化后，
 * 检测是否包含标注 {@link YdszGlobalTransactional} 的方法。若存在且 fallbackMode 非 FAIL，
 * 则为目标方法生成代理，拦截 Seata 事务异常并执行降级策略。
 *
 * <p><b>启用条件：</b>
 * <ul>
 *   <li>classpath 中存在 Seata（全局事务核心逻辑在 Seata Starter 中）</li>
 *   <li>方法标注了 {@code @YdszGlobalTransactional} 且 fallbackMode 非默认 {@link SeataFallbackMode#FAIL}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public class SeataFallbackBeanPostProcessor implements SmartInstantiationAwareBeanPostProcessor {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(SeataFallbackBeanPostProcessor.class);

    /** Seata 全局事务相关异常（通过反射判定，避免编译期依赖） */
    private static final String SEATA_TM_EXCEPTION = "io.seata.tm.api.TransactionException";
    private static final String SEATA_TM_TIMEOUT_EXCEPTION = "io.seata.tm.api.TransactionTimeoutException";
    private static final String SEATA_RM_EXCEPTION = "io.seata.rm.datasource.exception.TransactionRollbackFailedException";
    private static final String SEATA_RC_EXCEPTION = "io.seata.rm.datasource.exception.TransactionRollbackException";

    /** 缓存已解析的 fallbackMode（避免重复反射查询） */
    private static final ConcurrentHashMap<Method, SeataFallbackMode> FALLBACK_MODE_CACHE =
        new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = ClassUtils.getUserClass(bean.getClass());

        boolean hasFallbackMethod = false;
        for (Method method : targetClass.getDeclaredMethods()) {
            YdszGlobalTransactional annotation =
                AnnotatedElementUtils.findMergedAnnotation(method, YdszGlobalTransactional.class);
            if (annotation != null && annotation.fallbackMode() != SeataFallbackMode.FAIL) {
                hasFallbackMethod = true;
                FALLBACK_MODE_CACHE.put(method, annotation.fallbackMode());
                LOG.debug("Seata fallback mode {} registered for {}#{}",
                    annotation.fallbackMode(), targetClass.getSimpleName(), method.getName());
            }
        }

        if (!hasFallbackMethod) {
            return bean;
        }

        // 为此 Bean 生成代理，拦截标注 @YdszGlobalTransactional 方法
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new SeataFallbackMethodInterceptor());

        LOG.info("Seata fallback proxy created for bean: {}", targetClass.getSimpleName());
        return proxyFactory.getProxy(targetClass.getClassLoader());
    }

    /**
     * Seata 降级方法拦截器实现。
     *
     * <p>当目标方法执行时，如果 Seata 组件抛出了全局事务异常，根据配置的模式进行降级处理。
     */
    private static class SeataFallbackMethodInterceptor implements MethodInterceptor {

        /** 日志实例 */
        private static final Logger LOG = LoggerFactory.getLogger(SeataFallbackMethodInterceptor.class);

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            SeataFallbackMode fallbackMode = FALLBACK_MODE_CACHE.getOrDefault(method,
                SeataFallbackMode.FAIL);

            if (fallbackMode == SeataFallbackMode.FAIL) {
                // 降级策略为 FAIL 时直接放行，不做拦截
                return invocation.proceed();
            }

            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                if (isSeataUnavailable(ex)) {
                    return handleFallback(method, fallbackMode, ex, invocation);
                }
                throw ex;
            }
        }

        /**
         * 处理 Seata 不可用的降级逻辑。
         *
         * @param method 目标方法
         * @param fallbackMode 配置的降级模式
         * @param originalException 原始 Seata 异常
         * @param invocation 方法调用上下文
         * @return 降级逻辑返回值（SKIP 模式返回 null，LOCAL_TRANSACTION 重试执行）
         * @throws Throwable 当降级失败时抛出
         */
        private Object handleFallback(Method method, SeataFallbackMode fallbackMode,
                                     Throwable originalException, MethodInvocation invocation)
                throws Throwable {
            String methodName = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

            switch (fallbackMode) {
                case SKIP:
                    LOG.warn("Seata unavailable, SKIP mode triggered for {}: {}",
                        methodName, originalException.getMessage());
                    return getDefaultValue(method.getReturnType());
                case LOCAL_TRANSACTION:
                    LOG.warn("Seata unavailable, LOCAL_TRANSACTION fallback for {}: {}",
                        methodName, originalException.getMessage());
                    return invocation.getMethod().invoke(invocation.getThis(),
                        invocation.getArguments());
                default:
                    throw originalException;
            }
        }

        /**
         * 判断当前异常是否为 Seata 不可用导致的异常。
         *
         * <p>通过类名反射匹配，避免编译期对 Seata 的强制依赖。
         *
         * @param ex 待判断的异常
         * @return true = Seata 不可用异常
         */
        private boolean isSeataUnavailable(Throwable ex) {
            if (ex == null) {
                return false;
            }
            String exceptionClassName = ex.getClass().getName();
            return exceptionClassName.equals(SEATA_TM_EXCEPTION)
                || exceptionClassName.equals(SEATA_TM_TIMEOUT_EXCEPTION)
                || exceptionClassName.equals(SEATA_RM_EXCEPTION)
                || exceptionClassName.equals(SEATA_RC_EXCEPTION)
                || (exceptionClassName.startsWith("io.seata.")
                    && (exceptionClassName.contains("Exception")
                        || exceptionClassName.contains("Error")));
        }

        /**
         * 获取返回类型的默认值（用于 SKIP 模式）。
         *
         * @param returnType 返回类型
         * @return 对应默认值（null / 0 / false 等）
         */
        private Object getDefaultValue(Class<?> returnType) {
            if (returnType == null || returnType == void.class || returnType == Void.class) {
                return null;
            }
            if (returnType == boolean.class || returnType == Boolean.class) {
                return Boolean.FALSE;
            }
            if (returnType == int.class || returnType == Integer.class
                || returnType == long.class || returnType == Long.class
                || returnType == short.class || returnType == Short.class
                || returnType == byte.class || returnType == Byte.class) {
                return 0;
            }
            if (returnType == double.class || returnType == Double.class
                || returnType == float.class || returnType == Float.class) {
                return 0.0;
            }
            return null;
        }
    }
}
