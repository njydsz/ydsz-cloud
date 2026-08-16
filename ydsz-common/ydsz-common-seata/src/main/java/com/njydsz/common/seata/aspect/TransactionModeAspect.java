package com.njydsz.common.seata.aspect;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import com.njydsz.common.seata.annotation.TransactionalMode;
import com.njydsz.common.seata.api.TransactionType;
import com.njydsz.common.seata.context.XidContextHolder;

/**
 * 事务模式切面
 *
 * <p>拦截 {@link TransactionalMode} 注解声明的方法，在方法执行前自动切换
 * 到对应的事务类型，并在方法结束后清除上下文。
 *
 * <p><b>P1-6 新增</b>：解决业务代码中硬编码事务类型导致难以动态切换的问题。
 *
 * <p>设计说明：
 * <ul>
 *   <li>基于 {@link XidContextHolder} 的 TransmittableThreadLocal 标记事务上下文</li>
 *   <li>事务执行器通过 {@link XidContextHolder#getRequiredType()} 获取当前声明类型</li>
 *   <li>提供方法级别事务声明不影响不相关的其它调用</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Aspect
public class TransactionModeAspect implements Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionModeAspect.class);

    private final int order;

    /**
     * 构造事务模式切面
     *
     * @param order AOP 执行顺序值，数值越小优先级越高
     */
    public TransactionModeAspect(int order) {
        this.order = order;
    }

    /**
     * 拦截声明了 {@link TransactionalMode} 的方法
     *
     * <p>支持方法级别和类级别注解。优先级：方法级别 > 类级别。
     *
     * @param joinPoint  切点
     * @param annotation 事务模式注解
     * @return 方法返回值
     * @throws Throwable 方法异常
     */
    @Around(value = "execution(* *(..)) && (@annotation(annotation) || @within(annotation))",
            argNames = "joinPoint,annotation")
    public Object around(ProceedingJoinPoint joinPoint, TransactionalMode annotation) throws Throwable {
        TransactionType type = annotation.value();
        String txName = annotation.name();

        if (txName.isEmpty()) {
            txName = getMethodName(joinPoint);
        }

        XidContextHolder.setTransactionType(type, txName);

        if (LOG.isDebugEnabled()) {
            LOG.debug("[TxMode] Transaction type set: {}, method: {}", type, txName);
        }

        try {
            return joinPoint.proceed();
        } finally {
            XidContextHolder.remove();
        }
    }

    @Override
    public int getOrder() {
        return order;
    }

    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
