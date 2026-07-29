package com.njydsz.common.domain.specification;

/**
 * 查询规约接口 — DDD Specification 模式
 *
 * <p>封装业务规则为独立可组合的谓词对象，使查询条件可复用、可测试、可组合。
 * 对标 Spring Data JPA Specification / Domain-Driven Design Specification 模式。
 *
 * <p><b>核心语义：</b>
 * <ul>
 *   <li>单一职责：每个 Specification 封装一个业务规则</li>
 *   <li>可组合：通过 {@link #and}、{@link #or}、{@link #negate} 组合多个规约</li>
 *   <li>可测试：规约是独立对象，可脱离 Repository 单元测试</li>
 *   <li>可复用：同一规约可用于不同查询场景</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 定义规约
 * public class ActiveUserSpec implements Specification<User> {
 *     public boolean isSatisfiedBy(User user) {
 *         return "ACTIVE".equals(user.getStatus());
 *     }
 * }
 *
 * // 组合规约
 * Specification<User> spec = new ActiveUserSpec()
 *     .and(user -> user.getAge() >= 18)
 *     .and(user -> user.getEmail() != null);
 *
 * // 使用规约过滤
 * List<User> result = users.stream()
 *     .filter(spec::isSatisfiedBy)
 *     .collect(Collectors.toList());
 * }</pre>
 *
 * @param <T> 被检查的领域对象类型
 * @author ydsz-team
 * @since 1.2.0
 */
@FunctionalInterface
public interface Specification<T> {

    /**
     * 判断候选对象是否满足此规约
     *
     * @param candidate 候选对象
     * @return 满足返回 true，不满足返回 false
     */
    boolean isSatisfiedBy(T candidate);

    /**
     * 与另一个规约取交集（AND）
     *
     * @param other 另一个规约
     * @return 组合后的规约
     */
    default Specification<T> and(Specification<T> other) {
        return candidate -> this.isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }

    /**
     * 与另一个规约取并集（OR）
     *
     * @param other 另一个规约
     * @return 组合后的规约
     */
    default Specification<T> or(Specification<T> other) {
        return candidate -> this.isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    /**
     * 取反（NOT）
     *
     * @return 取反后的规约
     */
    default Specification<T> negate() {
        return candidate -> !this.isSatisfiedBy(candidate);
    }

    /**
     * 创建始终满足的规约
     *
     * @param <T> 对象类型
     * @return 始终返回 true 的规约
     */
    static <T> Specification<T> always() {
        return candidate -> true;
    }

    /**
     * 创建始终不满足的规约
     *
     * @param <T> 对象类型
     * @return 始终返回 false 的规约
     */
    static <T> Specification<T> never() {
        return candidate -> false;
    }

    /**
     * 包装一个规约（语义化入口）
     *
     * @param spec 规约
     * @param <T>  对象类型
     * @return 传入的规约
     */
    static <T> Specification<T> where(Specification<T> spec) {
        return spec != null ? spec : Specification.always();
    }
}
