package com.remisoft.common.domain.enums;

import com.remisoft.common.core.constant.HeaderConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 身份类型枚举
 *
 * <p>定义系统中用户的身份类型，用于区分不同级别的用户访问权限。
 *
 * <p><b>迁移计划（v1.7.0）：</b>
 * 此枚举属于业务配置，计划迁移至 {@code remi-userinfo-api} 模块。
 * 业务模块应定义自己的身份类型枚举，继承 {@link TypeEnum}。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>配合 HeaderConstants.X_IDENTITY_TYPE 请求头使用</li>
 *   <li>区分不同身份用户的访问控制和数据权限</li>
 *   <li>用户注册和登录时的身份验证</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @deprecated 1.7.0 计划迁移至 remi-userinfo-api 模块，使用 {@code UserIdentityType} 替代
 * @see HeaderConstants
 */
@Getter
@AllArgsConstructor
@Deprecated(since = "1.7.0", forRemoval = true)
public enum IdentityType implements TypeEnum<String> {

    REMI("remisoft", "云顶数字账号"),
    COMPANY("company", "集团公司账户"),
    VISITOR("visitor", "游客体验账号");

    /** 身份类型编码 */
    private final String code;
    /** 身份类型描述 */
    private final String desc;

    private static final Map<String, IdentityType> CODE_MAP = TypeEnum.buildCodeMap(IdentityType.class);

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
        return TypeEnum.codeOf(CODE_MAP, code);
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
