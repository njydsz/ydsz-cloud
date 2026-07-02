package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.DataScopeContext;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.common.security.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 数据权限 AOP
 *
 * <p>拦截 {@code @DataScope} 注解方法，将当前用户的数据权限上下文放入 ThreadLocal，
 * 业务 Service 在拼装 SQL 时通过 {@link DataScopeHelper} 取出。
 *
 * <p>该 AOP 不会自动改写 SQL（避免复杂的 SQL 解析），而是通过 ThreadLocal 传递上下文。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class DataScopeAspect {

    /** 当前线程数据权限上下文 */
    private static final ThreadLocal<DataScopeContext> CTX = new ThreadLocal<>();

    /**
     * 环绕增强：将数据权限上下文放入 ThreadLocal，方法执行完毕后清理
     *
     * @param pjp       连接点
     * @param dataScope 数据权限注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        try {
            DataScopeContext ctx = DataScopeHelper.current();
            CTX.set(ctx);
            log.debug("[DataScope] 进入数据权限 scope={} userId={} deptId={}",
                    ctx.getScope(), ctx.getUserId(), ctx.getDeptId());
            return pjp.proceed();
        } finally {
            CTX.remove();
        }
    }

    /**
     * 获取当前线程数据权限上下文（供业务层使用）
     *
     * @return 数据权限上下文；为空时从 SecurityContext 兜底构造
     */
    public static DataScopeContext peek() {
        DataScopeContext ctx = CTX.get();
        if (ctx == null) {
            // 兜底：从 SecurityContext 重新构造
            return DataScopeContext.from(SecurityContext.getCurrentOrNull());
        }
        return ctx;
    }

    /**
     * 越权检查：当前用户若无权访问目标部门则抛出 DATA_SCOPE_FORBIDDEN 异常
     *
     * @param targetDeptId 目标部门 ID
     * @throws BizException 无权限访问时抛出
     */
    public static void assertAllow(Long targetDeptId) {
        DataScopeContext ctx = peek();
        if (ctx.isAll()) {
            return;
        }
        if (targetDeptId == null) {
            return;
        }
        if (ctx.getDeptId() != null && ctx.getDeptId().equals(targetDeptId)) {
            return;
        }
        if (ctx.getCustomDeptIds() != null && ctx.getCustomDeptIds().contains(targetDeptId)) {
            return;
        }
        throw new BizException(BizErrorCode.DATA_SCOPE_FORBIDDEN, "无权访问部门: " + targetDeptId);
    }
}
