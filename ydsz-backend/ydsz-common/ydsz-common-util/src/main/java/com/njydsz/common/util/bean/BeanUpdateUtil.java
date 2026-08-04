package com.njydsz.common.util.bean;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;

import org.springframework.beans.BeanUtils;
/**
 * Bean 动态更新工具类。
 *
 * <p>核心能力：复制源对象中 <b>非 null</b> 的属性到目标对象，避免覆盖目标对象已有值。
 * 用于「PATCH 语义」的部分更新场景（PUT/POST 请求中 DTO 只携带需要变更的字段）。
 *
 * <h3>背景</h3>
 * <p>Spring 的 {@link org.springframework.beans.BeanUtils#copyProperties(Object, Object, String...)}
 * 默认会复制源对象的所有属性（包括 null），导致目标对象的已有字段被 null 覆盖。
 * 常见做法是手动传入 {@code getNullPropertyNames(source)} 数组作为忽略列表，
 * 但每个 Service 都重复实现一遍 {@code getNullPropertyNames} 既冗余又容易遗漏。
 *
 * <h3>统一方案</h3>
 * <p>本工具提供 {@link #copyNonNull(Object, Object, String...)} 方法，封装「忽略 null 属性 + 可选固定忽略属性」语义，
 * 替代各 Service 中分散的 {@code BeanUtils.copyProperties + getNullPropertyNames} 模式。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 仅复制 dto 中非 null 的字段到 entity，并额外忽略 id
 * BeanUpdateUtil.copyNonNull(dto, entity, "id");
 *
 * // 仅复制 dto 中非 null 的字段，并额外忽略 id 和 builtIn
 * BeanUpdateUtil.copyNonNull(dto, entity, "id", "builtIn");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class BeanUpdateUtil {

    private BeanUpdateUtil() {
    }

    /**
     * 复制源对象中 <b>非 null</b> 的属性到目标对象，可指定额外的固定忽略属性。
     *
     * <p>语义：
     * <ul>
     *   <li>源对象属性值为 {@code null} 的字段不会被复制（保留目标对象原值）</li>
     *   <li>{@code ignoreProperties} 中列出的属性名不会被复制（无论值是否为 null）</li>
     *   <li>其他非 null 属性会被复制到目标对象</li>
     * </ul>
     *
     * @param source           源对象（通常是 DTO）
     * @param target           目标对象（通常是 Entity）
     * @param ignoreProperties 额外固定忽略的属性名（如 "id"、"builtIn"），可为空
     * @param <T>              目标对象类型
     * @return 传入的 target 对象（便于链式调用）
     * @throws BeansException 如果属性访问失败
     */
    public static <T> T copyNonNull(Object source, T target, String... ignoreProperties) {
        if (source == null) {
            return target;
        }
        String[] combinedIgnore = combineNullPropertyNames(source, ignoreProperties);
        BeanUtils.copyProperties(source, target, combinedIgnore);
        return target;
    }

    /**
     * 计算源对象中值为 null 的属性名 + 额外固定忽略的属性名的合集。
     *
     * <p>抽取为独立方法便于单元测试与扩展（例如未来需要支持嵌套属性、Collection 类型忽略等）。
     *
     * @param source           源对象
     * @param ignoreProperties 额外固定忽略的属性名
     * @return 合并后的忽略属性名数组（不含重复项）
     */
    private static String[] combineNullPropertyNames(Object source, String... ignoreProperties) {
        final BeanWrapper wrapper = new BeanWrapperImpl(source);
        Set<String> ignored = new HashSet<>();
        // 1. 收集额外固定忽略属性
        if (ignoreProperties != null) {
            ignored.addAll(Arrays.asList(ignoreProperties));
        }
        // 2. 收集值为 null 的可读属性
        PropertyDescriptor[] pds = wrapper.getPropertyDescriptors();
        List<String> nullNames = new ArrayList<>(pds.length);
        for (PropertyDescriptor pd : pds) {
            String name = pd.getName();
            if (wrapper.isReadableProperty(name) && wrapper.getPropertyValue(name) == null) {
                nullNames.add(name);
            }
        }
        ignored.addAll(nullNames);
        return ignored.toArray(new String[0]);
    }
}
