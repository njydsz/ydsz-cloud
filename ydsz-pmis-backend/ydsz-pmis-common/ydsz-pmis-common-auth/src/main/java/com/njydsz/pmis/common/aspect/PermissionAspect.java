package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 权限校验 AOP
 *
 * <p>拦截 {@code @PrePermission} 注解方法，校验当前登录用户是否拥有所需权限。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    /**
     * 环绕增强：校验当前登录用户是否拥有注解声明的权限，校验通过则放行
     *
     * @param pjp           连接点
     * @param prePermission 权限注解
     * @return 目标方法返回值
     * @throws Throwable    目标方法抛出的异常
     * @throws BizException 未登录或无权限时抛出
     */
    @Around("@annotation(prePermission)")
    public Object around(ProceedingJoinPoint pjp, PrePermission prePermission) throws Throwable {
        LoginUser user = SecurityContext.getCurrentOrNull();

        if (prePermission.requireLogin() && user == null) {
            throw new BizException(BizErrorCode.UNAUTHORIZED);
        }

        if (user != null && prePermission.value().length > 0) {
            String[] requiredPerms = prePermission.value();
            PrePermission.Mode mode = prePermission.mode();

            boolean pass = switch (mode) {
                case AND -> Arrays.stream(requiredPerms).allMatch(user::hasPermission);
                case OR -> Arrays.stream(requiredPerms).anyMatch(user::hasPermission);
            };

            if (!pass) {
                log.warn("[Permission] 用户 {} 无权限访问 {} {}",
                        user.getUsername(), pjp.getSignature().toShortString(), Arrays.toString(requiredPerms));
                throw new BizException(BizErrorCode.FORBIDDEN, "error.common.msg_1e40057e", Arrays.toString(requiredPerms));
            }
        }

        return pjp.proceed();
    }
}
