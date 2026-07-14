package com.njydsz.pmis.common.domain.converter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.njydsz.pmis.common.core.enums.TypeEnum;

/**
 * TypeEnum 转换器工厂
 *
 * <p>提供 {@link TypeEnum} 枚举类型的通用转换能力，支持：
 * <ul>
 *   <li>根据 code 值查找枚举实例</li>
 *   <li>枚举实例转 code 值</li>
 *   <li>Spring MVC 请求参数自动绑定（String/Integer code → Enum）</li>
 *   <li>JSON 序列化/反序列化支持</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public enum OrderStatus implements TypeEnum<Integer> {
 *     PENDING(0, "待处理"),
 *     COMPLETED(1, "已完成"),
 *     CANCELLED(2, "已取消");
 *
 *     private final Integer code;
 *     private final String desc;
 *
 *     OrderStatus(Integer code, String desc) {
 *         this.code = code;
 *         this.desc = desc;
 *     }
 *
 *     &#64;Override
 *     public Integer getCode() { return code; }
 *
 *     &#64;Override
 *     public String getDesc() { return desc; }
 * }
 *
 * // 根据 code 查找枚举
 * OrderStatus status = TypeEnumConverterFactory.fromCode(OrderStatus.class, 1);
 * // 返回 COMPLETED
 *
 * // 枚举转 code
 * Integer code = TypeEnumConverterFactory.toCode(OrderStatus.COMPLETED);
 * // 返回 1
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see TypeEnum
 */
public final class TypeEnumConverterFactory {

    private TypeEnumConverterFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 根据 code 值查找枚举实例
     *
     * @param enumClass 枚举类
     * @param code      code 值
     * @param <T>       code 类型
     * @param <E>       枚举类型
     * @return 枚举实例
     * @throws IllegalArgumentException code 不存在时抛出
     */
    public static <T, E extends Enum<E> & TypeEnum<T>> E fromCode(Class<E> enumClass, T code) {
        if (code == null) {
            return null;
        }
        Map<T, E> codeMap = TypeEnum.buildCodeMap(enumClass);
        return TypeEnum.codeOf(codeMap, code);
    }

    /**
     * 根据 code 值查找枚举实例（不存在返回 null，不抛异常）
     *
     * @param enumClass 枚举类
     * @param code      code 值
     * @param <T>       code 类型
     * @param <E>       枚举类型
     * @return 枚举实例，不存在返回 null
     */
    public static <T, E extends Enum<E> & TypeEnum<T>> E fromCodeOrNull(Class<E> enumClass, T code) {
        if (code == null) {
            return null;
        }
        Map<T, E> codeMap = TypeEnum.buildCodeMap(enumClass);
        return codeMap.get(code);
    }

    /**
     * 获取枚举实例的 code 值
     *
     * @param enumValue 枚举实例
     * @param <T>       code 类型
     * @param <E>       枚举类型
     * @return code 值
     */
    public static <T, E extends Enum<E> & TypeEnum<T>> T toCode(E enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getCode();
    }

    /**
     * 获取枚举实例的描述信息
     *
     * @param enumValue 枚举实例
     * @return 描述信息
     */
    public static String toDesc(TypeEnum<?> enumValue) {
        if (enumValue == null) {
            return null;
        }
        return enumValue.getDesc();
    }

    /**
     * 获取枚举类所有可选项（用于前端下拉框等）
     *
     * @param enumClass 枚举类
     * @param <T>       code 类型
     * @param <E>       枚举类型
     * @return code 和 desc 的映射
     */
    public static <T, E extends Enum<E> & TypeEnum<T>> Map<T, String> options(Class<E> enumClass) {
        E[] constants = enumClass.getEnumConstants();
        return Arrays.stream(constants)
                .collect(Collectors.toMap(
                        TypeEnum::getCode,
                        TypeEnum::getDesc,
                        (a, b) -> a));
    }
}
