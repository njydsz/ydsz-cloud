package com.njydsz.userinfo.server.auth;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
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
 *   <li>不允许与最近 N 条历史密码重复（需配合 {@link UserPasswordHistoryService}）</li>
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
     * 校验密码强度（不检查历史密码，用于创建用户等无需检查历史的场景）。
     *
     * @param password 待校验密码
     * @param username 用户名（用于检查密码是否包含用户名）
     * @throws BusinessException 密码不符合策略时抛出
     */
    public void validate(String password, String username) {
        validate(password, username, null, null);
    }

    /**
     * 校验密码强度（含历史密码检查，用于修改密码场景）。
     *
     * <p>当 {@code passwordHistoryService} 和 {@code userId} 均不为 null 时，
     * 额外校验新密码是否与用户最近 {@code historyCount} 条历史密码重复。
     *
     * @param password 待校验密码
     * @param username 用户名（用于检查密码是否包含用户名）
     * @param userId 用户 ID（用于查询历史密码，为 null 时跳过历史检查）
     * @param passwordHistoryService 密码历史服务，为 null 时跳过历史检查
     * @throws BusinessException 密码不符合策略或与历史密码重复时抛出
     */
    public void validate(String password, String username, String userId,
                         UserPasswordHistoryService passwordHistoryService) {
        int minLength = properties.getPasswordMinLength();
        int maxLength = properties.getPasswordMaxLength();
        int minCategoryCount = properties.getPasswordMinCategoryCount();

        if (password == null || password.length() < minLength) {
            throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK,
                    new Object[]{"密码长度不能少于 " + minLength + " 个字符"});
        }
        if (password.length() > maxLength) {
            throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK,
                    new Object[]{"密码长度不能超过 " + maxLength + " 个字符"});
        }

        int categoryCount = 0;
        if (HAS_LOWER.matcher(password).find()) categoryCount++;
        if (HAS_UPPER.matcher(password).find()) categoryCount++;
        if (HAS_DIGIT.matcher(password).find()) categoryCount++;
        if (HAS_SPECIAL.matcher(password).find()) categoryCount++;

        if (categoryCount < minCategoryCount) {
            throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK,
                    "密码必须包含大写字母、小写字母、数字、特殊字符中的至少 " + minCategoryCount + " 种");
        }

        if (REPEAT_3.matcher(password).find()) {
            throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK,
                    "密码不允许连续 3 个以上重复字符");
        }

        if (username != null && !username.isBlank()) {
            if (password.toLowerCase().contains(username.toLowerCase())) {
                throw new BusinessException(UserInfoExceptionCode.PASSWORD_TOO_WEAK,
                        "密码不能包含用户名");
            }
        }

        // 历史密码校验（仅在提供 userId 和 passwordHistoryService 时执行）
        if (userId != null && passwordHistoryService != null) {
            int historyCount = properties.getPasswordHistoryCount();
            if (historyCount > 0
                    && passwordHistoryService.isPasswordReused(userId, password, historyCount)) {
                throw new BusinessException(UserInfoExceptionCode.PASSWORD_REUSED,
                        "不能使用最近 " + historyCount + " 次使用过的密码");
            }
        }
    }
}
