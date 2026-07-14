package com.njydsz.pmis.common.domain.entity;

import java.io.Serializable;

/**
 * 值对象标记接口
 *
 * <p>在领域驱动设计（DDD）中，值对象（Value Object）是没有唯一标识的领域对象，
 * 通过其属性值来定义相等性。值对象一旦创建通常不可变。
 *
 * <p><b>核心语义：</b>
 * <ul>
 *   <li><b>无标识：</b>值对象没有唯一标识，通过属性值判断相等性</li>
 *   <li><b>不可变性：</b>值对象创建后状态不可改变，修改操作返回新实例</li>
 *   <li><b>可替换性：</b>两个属性相同的值对象可互相替换，不影响业务逻辑</li>
 *   <li><b>无副作用：</b>值对象的方法不应修改自身状态，也不应产生副作用</li>
 * </ul>
 *
 * <p><b>与实体的区别：</b>
 * <table>
 *   <tr><th>维度</th><th>实体（Entity）</th><th>值对象（ValueObject）</th></tr>
 *   <tr><td>标识</td><td>有唯一标识</td><td>无标识，通过属性值区分</td></tr>
 *   <tr><td>相等性</td><td>标识相同即相等</td><td>所有属性相同即相等</td></tr>
 *   <tr><td>可变性</td><td>通常可变</td><td>通常不可变</td></tr>
 *   <tr><td>生命周期</td><td>有独立的生命周期</td><td>依附于实体，无独立生命周期</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public final class Money extends BaseValueObject {
 *     private final BigDecimal amount;
 *     private final String currency;
 *
 *     public Money(BigDecimal amount, String currency) {
 *         this.amount = amount;
 *         this.currency = currency;
 *     }
 *
 *     public Money add(Money other) {
 *         if (!this.currency.equals(other.currency)) {
 *             throw new DomainException("Currency mismatch");
 *         }
 *         return new Money(this.amount.add(other.amount), this.currency);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see BaseValueObject
 * @see Persistable
 */
public interface ValueObject extends Serializable {
}
