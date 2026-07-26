package com.njydsz.userinfo.server.auth;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;
import com.njydsz.userinfo.server.config.UserInfoProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 密码策略校验器。
 *
 * <p>对标互联网大厂密码安全标准：
 * <ul>
 *   <li>最小长度可配置（默认 8 字符）</li>
 *   <li>必须包含大小写字母、数字、特殊字符中的至少 N 种（可配置）</li>
 *   <li>不允许连续重复字符（如 aaa、111）</li>
 *   <li>不允许与用户名相同或包含用户名</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordPolicyValidator {

    private final UserInfoProperties properties;

    private static final Pattern HAS_LOWER = Pattern.compile("[a-z]");
    private static final Pattern HAS_UPPER = Pattern.compile("[A-Z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");
    private static final Pattern REPEAT_3 = Pattern.compile("(.)\\1{2,}");

    /**
     * 校验密码强度。
     *
     * @param password 待校验密码
     * @param username 用户名（用于检查密码是否包含用户名）
     * @throws BusinessException 密码不符合策略时抛出
     */
    public void validate(String password, String username) {
        int minLength = properties.getPasswordMinLength();
        int maxLength = properties.getPasswordMaxLength();
        int minCategoryCount = properties.getPasswordMinCategoryCount();

        if (password == null || password.length() < minLength) {
            throw new BusinessException(UserInfoResultCode.PASSWORD_TOO_WEAK,
                    "密码长度不能少于 " + minLength + " 个字符");
        }
        if (password.length() > maxLength) {
            throw new BusinessException(UserInfoResultCode.PASSWORD_TOO_WEAK,
                    "密码长度不能超过 " + maxLength + " 个字符");
        }

        int categoryCount = 0;
        if (HAS_LOWER.matcher(password).find()) categoryCount++;
        if (HAS_UPPER.matcher(password).find()) categoryCount++;
        if (HAS_DIGIT.matcher(password).find()) categoryCount++;
        if (HAS_SPECIAL.matcher(password).find()) categoryCount++;

        if (categoryCount < minCategoryCount) {
            throw new BusinessException(UserInfoResultCode.PASSWORD_TOO_WEAK,
                    "密码必须包含大写字母、小写字母、数字、特殊字符中的至少 " + minCategoryCount + " 种");
        }

        if (REPEAT_3.matcher(password).find()) {
            throw new BusinessException(UserInfoResultCode.PASSWORD_TOO_WEAK,
                    "密码不允许连续 3 个以上重复字符");
        }

        if (username != null && !username.isBlank()) {
            if (password.toLowerCase().contains(username.toLowerCase())) {
                throw new BusinessException(UserInfoResultCode.PASSWORD_TOO_WEAK,
                        "密码不能包含用户名");
            }
        }
    }
}
