package com.njydsz.common.domain.specification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Specification 规约模式单元测试
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("Specification 规约模式测试")
class SpecificationTest {

    @Test
    @DisplayName("where() 创建的规约应正确判断")
    void shouldEvaluateWhereSpecification() {
        Specification<String> notBlank = Specification.where(s -> s != null && !s.isBlank());
        assertTrue(notBlank.isSatisfiedBy("hello"));
        assertFalse(notBlank.isSatisfiedBy(""));
        assertFalse(notBlank.isSatisfiedBy(null));
    }

    @Test
    @DisplayName("always() 应始终返回 true")
    void shouldAlwaysReturnTrue() {
        Specification<String> always = Specification.always();
        assertTrue(always.isSatisfiedBy("anything"));
        assertTrue(always.isSatisfiedBy(null));
        assertTrue(always.isSatisfiedBy(""));
    }

    @Test
    @DisplayName("never() 应始终返回 false")
    void shouldAlwaysReturnFalse() {
        Specification<String> never = Specification.never();
        assertFalse(never.isSatisfiedBy("anything"));
        assertFalse(never.isSatisfiedBy(null));
    }

    @Test
    @DisplayName("and() 组合应同时满足两个规约")
    void shouldAndCombineTwoSpecs() {
        Specification<Integer> positive = Specification.where(n -> n > 0);
        Specification<Integer> even = Specification.where(n -> n % 2 == 0);
        Specification<Integer> positiveAndEven = positive.and(even);
        assertTrue(positiveAndEven.isSatisfiedBy(4));
        assertFalse(positiveAndEven.isSatisfiedBy(3));
        assertFalse(positiveAndEven.isSatisfiedBy(-2));
    }

    @Test
    @DisplayName("or() 组合应任一满足即可")
    void shouldOrCombineTwoSpecs() {
        Specification<Integer> positive = Specification.where(n -> n > 0);
        Specification<Integer> even = Specification.where(n -> n % 2 == 0);
        Specification<Integer> positiveOrEven = positive.or(even);
        assertTrue(positiveOrEven.isSatisfiedBy(4));
        assertTrue(positiveOrEven.isSatisfiedBy(3));
        assertTrue(positiveOrEven.isSatisfiedBy(-2));
        assertFalse(positiveOrEven.isSatisfiedBy(-3));
    }

    @Test
    @DisplayName("not() 应取反规约")
    void shouldNotInvertSpec() {
        Specification<Integer> positive = Specification.where(n -> n > 0);
        Specification<Integer> notPositive = positive.not();
        assertFalse(notPositive.isSatisfiedBy(5));
        assertTrue(notPositive.isSatisfiedBy(-1));
    }

    @Test
    @DisplayName("复杂组合：(A AND B) OR NOT(C)")
    void shouldSupportComplexCombination() {
        Specification<Integer> a = Specification.where(n -> n > 0);
        Specification<Integer> b = Specification.where(n -> n < 100);
        Specification<Integer> c = Specification.where(n -> n == 50);
        Specification<Integer> complex = a.and(b).or(c.not());
        assertTrue(complex.isSatisfiedBy(10));
        assertTrue(complex.isSatisfiedBy(200));
        assertFalse(complex.isSatisfiedBy(50));
    }

    @Test
    @DisplayName("AndSpecification 命名实现应正确工作")
    void shouldWorkWithNamedAndSpec() {
        Specification<Integer> left = Specification.where(n -> n > 0);
        Specification<Integer> right = Specification.where(n -> n < 10);
        AndSpecification<Integer> andSpec = new AndSpecification<>(left, right);
        assertTrue(andSpec.isSatisfiedBy(5));
        assertFalse(andSpec.isSatisfiedBy(15));
        assertFalse(andSpec.isSatisfiedBy(-1));
    }

    @Test
    @DisplayName("OrSpecification 命名实现应正确工作")
    void shouldWorkWithNamedOrSpec() {
        Specification<Integer> left = Specification.where(n -> n < 0);
        Specification<Integer> right = Specification.where(n -> n > 100);
        OrSpecification<Integer> orSpec = new OrSpecification<>(left, right);
        assertTrue(orSpec.isSatisfiedBy(-1));
        assertTrue(orSpec.isSatisfiedBy(101));
        assertFalse(orSpec.isSatisfiedBy(50));
    }

    @Test
    @DisplayName("NotSpecification 命名实现应正确工作")
    void shouldWorkWithNamedNotSpec() {
        Specification<Integer> original = Specification.where(n -> n > 0);
        NotSpecification<Integer> notSpec = new NotSpecification<>(original);
        assertFalse(notSpec.isSatisfiedBy(5));
        assertTrue(notSpec.isSatisfiedBy(-1));
    }
}
