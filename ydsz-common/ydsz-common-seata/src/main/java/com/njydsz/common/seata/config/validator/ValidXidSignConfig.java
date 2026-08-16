package com.njydsz.common.seata.config.validator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * XID 签名配置校验注解
 *
 * <p>校验规则：当 {@code xidSignEnabled=true} 时，{@code xidSignKey} 不能为空或空白字符串。
 *
 * <p><b>P2-2 新增</b>：解决 XID 签名配置不完整导致运行时签名失败的问题。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Documented
@Constraint(validatedBy = {XidSignConfigValidator.class})
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidXidSignConfig {

    String message() default "当 ydsz.seata.xid-sign-enabled=true 时，ydsz.seata.xid-sign-key 不能为空";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
