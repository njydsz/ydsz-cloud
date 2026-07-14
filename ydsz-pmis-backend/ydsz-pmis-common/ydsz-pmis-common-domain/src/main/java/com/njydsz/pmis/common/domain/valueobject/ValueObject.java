package com.njydsz.pmis.common.domain.valueobject;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 值对象标记接。
 *
 * <p>在领域驱动设计（DDD）中，值对象（Value Object）是通过其属性值来定义的对象，
 * 而非通过标识。值对象具有不可变性和相等性两个核心特征。
 *
 * <p><b>核心语义：</b>
 * <ul>
 *   <li><b>无独立标识：</b>值对象的身份由其属性值决定，而非唯一ID</li>
 *   <li><b>不可变性：</b>值对象一旦创建，其状态不可改变，修改操作应返回新的实。</li>
 *   <li><b>相等性：</b>两个值对象的所有属性值都相等时，它们就是相等。</li>
 *   <li><b>可替换性：</b>相等的值对象可以互相替换，不影响业务逻辑</li>
 * </ul>
 *
 * <p><b>equals/hashCode 建议：</b>
 * <p>值对象的 {@code equals} 为 {@code hashCode} 应基于所有属性值实现，
 * 而非基于对象引用。本接口提供默认方法建议，实现类应确保：
 * <ul>
 *   <li>{@code equals} 比较所有业务属性。</li>
 *   <li>{@code hashCode} 基于所有业务属性值计。</li>
 *   <li>属性值相同则 equals 返回 true，hashCode 相同</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public final class Money implements ValueObject {
 *     private final BigDecimal amount;
 *     private final String currency;
 *
 *     public Money(BigDecimal amount, String currency) {
 *         this.amount = amount;
 *         this.currency = currency;
 *     }
 *
 *     public Money add(Money other) {
 *         return new Money(this.amount.add(other.amount), this.currency);
 *     }
 *
 *     &#64;Override
 *     public boolean equals(Object o) {
 *         if (this == o) return true;
 *         if (!(o instanceof Money)) return false;
 *         Money money = (Money) o;
 *         return amount.compareTo(money.amount) == 0
 *                 && currency.equals(money.currency);
 *     }
 *
 *     &#64;Override
 *     public int hashCode() {
 *         return Objects.hash(amount, currency);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public interface ValueObject extends Serializable {

    /**
     * 获取值对象的所有属性值，用于默认。equals/hashCode 计算
     *
     * <p>实现类应返回包含所有业务属性值的数组。
     * 默认实现返回空数组，建议实现类覆盖此方法。
     *
     * @return 属性值数。
     */
    default Object[] getValues() {
        return new Object[]{};
    }

    /**
     * 基于属性值的默认 equals 实现
     *
     * <p>比较两个值对象的所有属性值是否相等。
     * 实现类可直接使用此默认实现，也可覆盖以提供更高效的实现。
     *
     * @param other 另一个对。
     * @return 如果所有属性值相等则返回 true
     */
    default boolean valueEquals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ValueObject that = (ValueObject) other;
        return Arrays.deepEquals(this.getValues(), that.getValues());
    }

    /**
     * 基于属性值的默认 hashCode 实现
     *
     * <p>基于所有属性值计算哈希码，确保相等的值对象具有相同的哈希码。
     * 实现类可直接使用此默认实现，也可覆盖以提供更高效的实现。
     *
     * @return 基于属性值的哈希。
     */
    default int valueHashCode() {
        return Arrays.deepHashCode(getValues());
    }
}
