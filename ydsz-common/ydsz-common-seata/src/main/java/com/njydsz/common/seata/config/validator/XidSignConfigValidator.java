package com.njydsz.common.seata.config.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.njydsz.common.seata.config.SeataProperties;

/**
 * XID 签名配置校验器
 *
 * <p>实现 {@link ValidXidSignConfig} 注解的校验逻辑：
 * 当 {@code xidSignEnabled} 为 true 时，验证 {@code xidSignKey} 非空。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class XidSignConfigValidator implements ConstraintValidator<ValidXidSignConfig, SeataProperties> {

    @Override
    public boolean isValid(SeataProperties properties, ConstraintValidatorContext context) {
        if (!properties.isXidSignEnabled()) {
            // 未启用签名时不检查 key
            return true;
        }

        String signKey = properties.getXidSignKey();
        if (signKey == null || signKey.isBlank()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "ydsz.seata.xid-sign-key 不能为空或空白（当前 ydsz.seata.xid-sign-enabled=true）")
                    .addPropertyNode("xidSignKey")
                    .addConstraintViolation();
            return false;
        }

        // 建议最小密钥长度
        if (signKey.length() < 16) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "ydsz.seata.xid-sign-key 长度建议不少于 16 个字符，当前长度: " + signKey.length())
                    .addPropertyNode("xidSignKey")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
