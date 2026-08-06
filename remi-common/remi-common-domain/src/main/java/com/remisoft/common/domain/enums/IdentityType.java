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
 * <p><b>迁移计划：</b>
 * 此枚举计划迁移至 {@code remi-userinfo-api} 模块，由业务模块定义自己的身份类型枚举。
 * 现有引用模块：common-util、common-feign。
 * 请这些模块在 1.9.0 之前完成迁移，使用本地定义的枚举替代。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>配合 HeaderConstants.X_IDENTITY_TYPE 请求头使用</li>
 *   <li>区分不同身份用户的访问权限</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @deprecated 1.7.0 迁移至业务模块，1.9.0 移除
 * @see HeaderConstants
 * @see TypeEnum 替代实现基类
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
