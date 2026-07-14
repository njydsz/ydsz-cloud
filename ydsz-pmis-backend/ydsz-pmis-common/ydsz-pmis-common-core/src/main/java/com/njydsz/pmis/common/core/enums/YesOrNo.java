package com.njydsz.pmis.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 是/否枚举定义
 *
 * <p>用于表示二元状态，是系统中最基础的布尔表示方式。
 * 相比原生 boolean 类型，此枚举更适用于数据库存储和 API 交互场景。
 *
 * <p><b>编码说明：</b>
 * <ul>
 *   <li>YES: code = 1，表示"是/真"</li>
 *   <li>NO: code = 0，表示"否/假"</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>数据库中用 tinyint/int 类型存储布尔值</li>
 *   <li>API 响应中表示操作成功/失败状态</li>
 *   <li>业务状态判断（如是否启用、是否删除等）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Getter
@AllArgsConstructor
public enum YesOrNo implements TypeEnum<Integer> {

    /**
     * 否/假
     */
    NO(0, "否"),

    /**
     * 是/真
     */
    YES(1, "是");

    /**
     * 枚举编码
     */
    private final Integer code;

    /**
     * 枚举描述
     */
    private final String desc;

    /** 按枚举编码索引的不可变映射，用于通过编码快速查找枚举值 */
    private static final Map<Integer, YesOrNo> CODE_MAP;

    static {
        CODE_MAP = Collections.unmodifiableMap(
                Arrays.stream(values())
                        .collect(Collectors.toMap(YesOrNo::getCode, Function.identity()))
        );
    }

    /**
     * 根据 Integer 编码获取是/否枚举（安全版本）
     *
     * @param code 编码值
     * @return 对应的枚举值，未找到返回 null
     */
    public static YesOrNo of(Integer code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }

    /**
     * 根据 Integer 编码获取是/否枚举
     *
     * @param code 编码值
     * @return 对应的枚举值
     * @throws IllegalArgumentException 当编码为 null 时抛出
     */
    public static YesOrNo codeOf(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        return CODE_MAP.get(code);
    }

    /**
     * 根据 String 编码获取是/否枚举（安全版本）
     *
     * @param code 编码字符串
     * @return 对应的枚举值，未找到或格式不正确返回 null
     */
    public static YesOrNo of(String code) {
        if (code == null) {
            return null;
        }
        try {
            return of(Integer.valueOf(code));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 根据 String 编码获取是/否枚举
     *
     * @param code 编码字符串
     * @return 对应的枚举值
     * @throws IllegalArgumentException 当编码为 null 或找不到对应枚举时抛出
     */
    public static YesOrNo codeOf(String code) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        YesOrNo result = of(code);
        if (result == null) {
            throw new IllegalArgumentException("No YesOrNo constant with code: " + code);
        }
        return result;
    }

    /**
     * 检查 Integer 编码是否有效
     *
     * @param code 编码
     * @return 有效返回true，否则返回false
     */
    public static boolean isValidCode(Integer code) {
        return of(code) != null;
    }

    /**
     * 检查 String 编码是否有效
     *
     * @param code 编码字符串
     * @return 有效返回true，否则返回false
     */
    public static boolean isValidCode(String code) {
        return of(code) != null;
    }

    /**
     * 判断当前枚举是否为 YES
     *
     * @return 是YES返回true，否则返回false
     */
    public boolean isYes() {
        return YES.code.equals(this.code);
    }

    /**
     * 判断当前枚举是否为 NO
     *
     * @return 是NO返回true，否则返回false
     */
    public boolean isNo() {
        return NO.code.equals(this.code);
    }
}