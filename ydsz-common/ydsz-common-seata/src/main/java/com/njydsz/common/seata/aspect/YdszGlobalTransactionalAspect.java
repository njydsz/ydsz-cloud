package com.njydsz.common.seata.aspect;

import com.njydsz.common.seata.annotation.YdszGlobalTransactional;
import com.njydsz.common.seata.config.SeataProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 全局事务切面：驱动 {@link YdszGlobalTransactional} 标注的方法在 Seata 全局事务上下文中执行。
 *
 * <p>实现方式采用<b>反射</b>：通过 {@link SeataReflector} 调用 Seata 全局事务 API
 * （{@code io.seata.tm.api.GlobalTransaction}），避免本模块编译期对 Seata 的硬依赖，
 * 符合 YDIZ-COMMON-003（所有 Seata 能力通过 {@code com.njydsz.common.seata.*} 封装）。
 *
 * <p>Seata 全局事务实际由业务方引入 Seata 客户端后，基于其打包的自动扫描器
 * （{@code io.seata.spring.annotation.GlobalTransactionScanner}）在内嵌注册
 * {@link org.springframework.beans.factory.config.BeanPostProcessor}，由 Spring 在生命周期钩子内创建代理。
 * 当 classpath 存在 Seata 客户端时，本切面通过 {@link io.seata.core.context.RootContext}
 * + {@code io.seata.tm.api.GlobalTransactionContext} 实现无传递地跨方法。
 *
 * <p><b>禁用条件</b>
 * <ul>
 *   <li>{@code SeataProperties.enabled == false} 时切面不执行；</li>
 *   <li>Seata 客户端不在 classpath（{@link SeataReflector#seataPresent()}）
 *       时直接放行，跳过全局事务逻辑（warn 一次）。</li>
 * </ul>
 *
 * <p>有序性：本切面优先级 {@link Ordered#LOWEST_PRECEDENCE}，以保证在本地
 * {@code @Transactional} 等切面内部嵌套或被嵌套时仍可正常工作
 * （业务侧应遵循 YDIZ-TX-001 禁止嵌套全局事务）。
 *
 * @author ydsz-team
 * @since ACC-1
 */
@Slf4j
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class YdszGlobalTransactionalAspect {

  private final SeataProperties properties;

  @Around("@annotation(com.njydsz.common.seata.annotation.YdszGlobalTransactional)")
  public Object around(final ProceedingJoinPoint joinPoint) throws Throwable {
    if (!properties.isEnabled()) {
      return joinPoint.proceed();
    }
    if (!SeataReflector.seataPresent()) {
      log.warn(
          "Seata 客户端未在 classpath 中（业务方未引入 seata 相关依赖），"
              + "@YdszGlobalTransactional(\"{}\") 直接放行，跳过全局事务逻辑。"
              + "建议配置 ydsz.seata.enabled=false 以消除此警告。",
          joinPoint.getSignature().toShortString());
      return joinPoint.proceed();
    }

    final YdszGlobalTransactional ann = resolveAnnotation(joinPoint);
    final String xid = SeataReflector.beginGlobalTx(joinPoint, ann);
    if (xid != null) {
      log.debug("Seata 全局事务已开启, method={}, xid={}", joinPoint.getSignature().getName(), xid);
    }

    try {
      final Object result = joinPoint.proceed();
      SeataReflector.commit();
      return result;
    } catch (Throwable ex) {
      if (shouldRollbackOn(ann, ex)) {
        SeataReflector.rollback();
        log.warn(
            "Seata 全局事务已回滚, method={}, error={}",
            joinPoint.getSignature().getName(),
            ex.toString());
      } else {
        SeataReflector.commit();
      }
      throw ex;
    }
  }

  /** 解析方法上的 {@link YdszGlobalTransactional} */
  private YdszGlobalTransactional resolveAnnotation(final ProceedingJoinPoint joinPoint) {
    final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    YdszGlobalTransactional ann = method.getAnnotation(YdszGlobalTransactional.class);
    if (ann == null) {
      try {
        method =
            joinPoint
                .getTarget()
                .getClass()
                .getMethod(method.getName(), method.getParameterTypes());
        ann = method.getAnnotation(YdszGlobalTransactional.class);
      } catch (NoSuchMethodException e) {
        log.error("无法解析 @YdszGlobalTransactional 注解, method={}", method, e);
      }
    }
    return ann;
  }

  /** 根据 rollbackFor / noRollbackFor 判断当前异常是否应触发回滚 */
  private boolean shouldRollbackOn(final YdszGlobalTransactional ann, final Throwable ex) {
    if (Arrays.asList(ann.noRollbackFor()).contains(ex.getClass())) {
      return false;
    }
    if (ann.rollbackFor().length == 0) {
      return true;
    }
    return Arrays.stream(ann.rollbackFor()).anyMatch(cls -> cls.isInstance(ex));
  }
}
