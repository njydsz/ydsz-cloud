package com.njydsz.common.domain.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 值对象抽象基类
 *
 * <p>提供基于属性值的 {@code equals}、{@code hashCode} 和 {@code toString} 默认实现。
 * 子类应将所有字段声明为 {@code final} 以保证不可变性。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>不可变性：所有字段应为 final，修改操作返回新实例</li>
 *   <li>基于属性的相等性：两个实例所有属性相同即为相等</li>
 *   <li>无标识：不包含 id 字段，不继承 {@link Persistable}</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public final class Address extends BaseValueObject {
 *     private final String province;
 *     private final String city;
 *     private final String detail;
 *
 *     public Address(String province, String city, String detail) {
 *         this.province = province;
 *         this.city = city;
 *         this.detail = detail;
 *     }
 *
 *     // getter methods...
 * }
 *
 * Address a1 = new Address("广东省", "深圳市", "南山区");
 * Address a2 = new Address("广东省", "深圳市", "南山区");
 * a1.equals(a2); // true
 * a1.toString();  // Address{province=广东省, city=深圳市, detail=南山区}
 * }</pre>
 *
 * <p><b>注意：</b>子类必须将所有字段声明为 {@code final} 以保证不可变性。
 * 如果子类包含可变字段，则不应继承此类。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ValueObject
 */
public abstract class BaseValueObject implements ValueObject {

    private static final long serialVersionUID = 1L;

    /**
     * 获取值对象的所有属性值，用于相等性比较和哈希码计算
     *
     * <p>子类必须实现此方法，返回所有参与相等性判断的属性名与属性值。
     * 使用 {@link LinkedHashMap} 保证迭代顺序稳定，确保 {@code hashCode} 的稳定性。
     *
     * @return 属性名到属性值的映射
     */
    protected abstract Map<String, Object> identityValues();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BaseValueObject other = (BaseValueObject) obj;
        return Objects.equals(this.identityValues(), other.identityValues());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(identityValues());
    }

    @Override
    public String toString() {
        Map<String, Object> values = identityValues();
        String simpleName = this.getClass().getSimpleName();
        if (values == null || values.isEmpty()) {
            return simpleName + "{}";
        }
        StringBuilder sb = new StringBuilder(simpleName).append('{');
        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            i++;
        }
        return sb.append('}').toString();
    }
}
