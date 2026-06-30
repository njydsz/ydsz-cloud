package com.njydsz.pmis.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResult 分页结果测试")
class PageResultTest {

    @Test
    @DisplayName("empty() 应返回空列表")
    void empty_returnsEmptyList() {
        PageResult<String> result = PageResult.empty();
        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPages()).isZero();
    }

    @Test
    @DisplayName("of() 应正确计算总页数")
    void of_calculatesPages() {
        PageResult<String> r = PageResult.of(List.of("a", "b", "c"), 25, 1, 10);
        assertThat(r.getTotal()).isEqualTo(25);
        assertThat(r.getSize()).isEqualTo(10);
        assertThat(r.getPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("of() 整除场景应计算正确")
    void of_exactDivision() {
        PageResult<String> r = PageResult.of(List.of(), 30, 3, 10);
        assertThat(r.getPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("of() size=0 应避免除零异常")
    void of_zeroSize() {
        PageResult<String> r = PageResult.of(List.of(), 0, 1, 0);
        assertThat(r.getPages()).isZero();
    }
}
