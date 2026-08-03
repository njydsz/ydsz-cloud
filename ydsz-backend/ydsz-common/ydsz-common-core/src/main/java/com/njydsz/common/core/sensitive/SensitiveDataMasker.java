package com.njydsz.common.core.sensitive;

import java.lang.reflect.Field;

/**
 * 敏感数据脱敏工具类。
 *
 * <p>提供纯 Java 的脱敏算法实现（与 JSON 引擎解耦），
 * 支持通过 {@link Sensitive} 注解 + 反射对任意对象的敏感字段执行脱敏。</p>
 *
 * <p><b>使用方式：</b></p>
 * <pre>{@code
 * // 方式一：直接按类型脱敏单个值
 * String masked = SensitiveDataMasker.mask("13812345678", SensitiveType.MOBILE); // 138****5678
 *
 * // 方式二：对对象的敏感字段（@Sensitive 标注）执行脱敏
 * UserVO vo = ...;
 * SensitiveDataMasker.maskObject(vo); // 直接修改 vo 的敏感字段为脱敏值
 * }</pre>
 *
 * <p><b>脱敏规则：</b></p>
 * <ul>
 *   <li>输入为 null 或空字符串时原样返回</li>
 *   <li>长度不足时退化为全部打码（保证不泄露原始信息）</li>
 *   <li>非 String 类型的字段跳过（仅处理 String 字段）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see Sensitive
 * @see SensitiveType
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按指定类型对敏感值执行脱敏。
     *
     * @param value 原始敏感值
     * @param type  敏感数据类型
     * @return 脱敏后的值；输入为空或类型为 null 时原样返回
     */
    public static String mask(String value, SensitiveType type) {
        if (value == null || value.isEmpty() || type == null) {
            return value;
        }
        return switch (type) {
            case MOBILE -> maskMobile(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case EMAIL -> maskEmail(value);
            case NAME -> maskName(value);
            case ADDRESS -> maskAddress(value);
            case PASSWORD -> maskPassword(value);
            case CUSTOM -> value; // CUSTOM 需通过 maskObject 或显式 masker 处理
        };
    }

    /**
     * 对指定对象的 {@link Sensitive} 标注字段执行就地脱敏。
     *
     * <p>递归处理父类字段（含私有字段），仅修改 String 类型字段。
     * 遇到无法访问的字段时跳过（不抛异常）。</p>
     *
     * @param object 目标对象（原地修改）；null 时直接返回
     */
    public static void maskObject(Object object) {
        if (object == null) {
            return;
        }
        Class<?> clazz = object.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                Sensitive annotation = field.getAnnotation(Sensitive.class);
                if (annotation == null || field.getType() != String.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object raw = field.get(object);
                    if (!(raw instanceof String value)) {
                        continue;
                    }
                    field.set(object, doMask(value, annotation));
                } catch (IllegalAccessException ignored) {
                    // 跳过无法访问的字段
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 根据注解执行脱敏（处理 CUSTOM 类型）。
     */
    private static String doMask(String value, Sensitive annotation) {
        if (annotation.type() == SensitiveType.CUSTOM) {
            SensitiveDataMasker.SensitiveMasker masker = instantiateMasker(annotation.masker());
            return masker != null ? masker.mask(value) : value;
        }
        return mask(value, annotation.type());
    }

    /**
     * 实例化自定义脱敏器（无参构造，失败返回 null）。
     */
    private static SensitiveMasker instantiateMasker(Class<? extends SensitiveMasker> maskerClass) {
        if (maskerClass == null || maskerClass == DefaultMasker.class) {
            return null;
        }
        try {
            return maskerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    // ==================== 各类型脱敏算法 ====================

    /**
     * 手机号脱敏：保留前 3 后 4。
     *
     * @param value 原始值
     * @return 如 {@code 138****5678}
     */
    static String maskMobile(String value) {
        if (value.length() < 7) {
            return maskAll(value);
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 身份证脱敏：保留前 4 后 4。
     *
     * @param value 原始值
     * @return 如 {@code 3201**********1234}
     */
    static String maskIdCard(String value) {
        if (value.length() < 8) {
            return maskAll(value);
        }
        return value.substring(0, 4) + maskAll(value.substring(4, value.length() - 4))
                + value.substring(value.length() - 4);
    }

    /**
     * 银行卡脱敏：保留前 4 后 4。
     *
     * @param value 原始值
     * @return 如 {@code 6222 **** 1234}
     */
    static String maskBankCard(String value) {
        if (value.length() < 8) {
            return maskAll(value);
        }
        return value.substring(0, 4) + " **** " + value.substring(value.length() - 4);
    }

    /**
     * 邮箱脱敏：保留首字符与 @ 后域名。
     *
     * @param value 原始值
     * @return 如 {@code z***@example.com}
     */
    static String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex <= 1) {
            return maskAll(value);
        }
        String prefix = value.substring(0, atIndex);
        String suffix = value.substring(atIndex);
        return prefix.charAt(0) + maskAll(prefix.substring(1)) + suffix;
    }

    /**
     * 姓名脱敏：保留姓氏（首个字符）。
     *
     * @param value 原始值
     * @return 如 {@code 张*}
     */
    static String maskName(String value) {
        if (value.length() <= 1) {
            return maskAll(value);
        }
        return value.charAt(0) + "*";
    }

    /**
     * 地址脱敏：保留前 6 个字符（省市区），其余打码。
     *
     * @param value 原始值
     * @return 如 {@code 江苏省南京市********}
     */
    static String maskAddress(String value) {
        if (value.length() <= 6) {
            return maskAll(value);
        }
        return value.substring(0, 6) + maskAll(value.substring(6));
    }

    /**
     * 密码脱敏：全部替换为固定占位符。
     *
     * @param value 原始值
     * @return {@code ******}
     */
    static String maskPassword(String value) {
        return "******";
    }

    /**
     * 全部打码（长度不足时的降级策略）。
     *
     * @param value 原始值
     * @return 等长 {@code *} 串
     */
    private static String maskAll(String value) {
        return "*".repeat(value.length());
    }

    /**
     * 自定义脱敏器 SPI。
     *
     * <p>实现类必须提供公开无参构造函数，通过
     * {@link Sensitive#masker()} 指定。</p>
     */
    @FunctionalInterface
    public interface SensitiveMasker {

        /**
         * 执行脱敏。
         *
         * @param value 原始值（非 null）
         * @return 脱敏后的值
         */
        String mask(String value);
    }

    /**
     * 默认脱敏器（占位，表示未指定自定义实现）。
     */
    public static final class DefaultMasker implements SensitiveMasker {
        @Override
        public String mask(String value) {
            return value;
        }
    }
}
