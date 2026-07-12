package com.njydsz.pmis.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 身份类型枚举
 *
 * <p>定义系统中用户的身份类型，用于区分不同级别的用户访问权限。
 * 支持瑞米软件账号、集团公司账户、游客体验账号三种类型。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>配合 HeaderConstants.X_IDENTITY_TYPE 请求头使用</li>
 *   <li>区分不同身份用户的访问控制和数据权限</li>
 *   <li>用户注册和登录时的身份验证</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see com.njydsz.pmis.common.core.constant.HeaderConstants
 */
@Getter
@AllArgsConstructor
public enum IdentityType implements TypeEnum<String> {

    REMISOFT("remisoft", "瑞米软件账号"),
    COMPANY("company", "集团公司账户"),
    VISITOR("visitor", "游客体验账号");

    /** 身份类型编码 */
    private final String code;
    /** 身份类型描述 */
    private final String desc;

    private static final Map<String, IdentityType> CODE_MAP;

    static {
        CODE_MAP = Collections.unmodifiableMap(
                Arrays.stream(values())
                        .collect(Collectors.toMap(IdentityType::getCode, Function.identity()))
        );
    }

    /**
     * 根据编码获取身份类型（安全版本）
     *
     * @param code 编码值
     * @return 对应的枚举值，未找到返回 null
     */
    public static IdentityType of(String code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }

    /**
     * 根据编码获取身份类型
     *
     * @param code 编码值
     * @return 对应的枚举值
     * @throws IllegalArgumentException 当编码不存在时抛出
     */
    public static IdentityType codeOf(String code) {
        IdentityType value = of(code);
        if (value == null) {
            throw new IllegalArgumentException("Unknown IdentityType code: " + code);
        }
        return value;
    }

    /**
     * 检查编码是否有效
     *
     * @param code 编码值
     * @return 有效返回 true，否则返回 false
     */
    public static boolean isValidCode(String code) {
        return of(code) != null;
    }
}