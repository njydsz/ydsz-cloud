package com.njydsz.common.core.annotation;

/**
 * 敏感数据类型枚举。
 *
 * <p>定义系统支持的脱敏类型及其对应的格式化规则。
 * 新增类型只需在此枚举中添加即可，无需修改脱敏器逻辑。</p>
 *
 * @author ydsz-team
 * @since 1.5.0
 * @see Sensitive
 */
public enum SensitiveType {

    /**
     * 身份证号：保留前 3 位和后 4 位，中间替换为星号。
     *
     * <p>示例：{@code 320102199001011234 → 320***********1234}
     */
    ID_CARD(3, 4),

    /**
     * 手机号：保留前 3 位和后 4 位，中间替换为星号。
     *
     * <p>示例：{@code 13812345678 → 138****5678}
     */
    MOBILE(3, 4),

    /**
     * 邮箱：保留首字母和末字母（@前），域名部分完整显示。
     *
     * <p>示例：{@code zhangsan@example.com → z***n@example.com}
     */
    EMAIL,

    /**
     * 银行卡号：保留前 4 位和后 4 位，中间替换为星号。
     *
     * <p>示例：{@code 6222021234567890 → 6222****7890}
     */
    BANK_CARD(4, 4),

    /**
     * 姓名：保留首字，其余替换为星号（单字姓名全显示）。
     *
     * <p>示例：{@code 张三 → 张*}，{@code 欧阳锋 → 欧**}
     */
    NAME,

    /**
     * 地址：保留前 6 个字符，其余替换为星号。
     *
     * <p>示例：{@code 北京市海淀区中关村大街1号 → 北京市海淀区****}
     */
    ADDRESS,

    /**
     * 全部掩码：仅保留首字符，其余全替换为星号。
     *
     * <p>适用于无特定格式的敏感字段。
     */
    MASK_ALL;

    private final int prefixKeep;
    private final int suffixKeep;
    private final boolean isCustom;

    SensitiveType() {
        this.prefixKeep = 0;
        this.suffixKeep = 0;
        this.isCustom = false;
    }

    SensitiveType(int prefixKeep, int suffixKeep) {
        this.prefixKeep = prefixKeep;
        this.suffixKeep = suffixKeep;
        this.isCustom = false;
    }

    /**
     * 头部保留字符数。
     */
    public int getPrefixKeep() {
        return prefixKeep;
    }

    /**
     * 尾部保留字符数。
     */
    public int getSuffixKeep() {
        return suffixKeep;
    }

    /**
     * 是否为自定义脱敏类型。
     * <p>自定义类型的前缀/后缀保留数由 {@link Sensitive} 注解中的参数决定。</p>
     */
    public boolean isCustom() {
        return isCustom;
    }

    /**
     * 自定义脱敏类型（前缀/后缀保留数由注解参数控制）。
     *
     * @since 1.5.0
     */
    public static final SensitiveType CUSTOM = new SensitiveType() {
        @Override
        public boolean isCustom() {
            return true;
        }
    };
}
