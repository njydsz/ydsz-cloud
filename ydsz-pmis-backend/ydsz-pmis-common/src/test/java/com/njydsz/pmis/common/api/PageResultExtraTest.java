package com.njydsz.pmis.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PageResult 单元测试（扩充）
 */
@DisplayName("PageResult 分页结果测试")
class PageResultExtraTest {

    @Test
    @DisplayName("of() 整除场景应正确")
    void of_exact() {
        PageResult<String> r = PageResult.of(List.of("a", "b"), 10, 2, 5);
        assertThat(r.getPages()).isEqualTo(2);
        assertThat(r.getPage()).isEqualTo(2);
        assertThat(r.getSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("of() 不可整除应向上取整")
    void of_ceil() {
        PageResult<String> r = PageResult.of(new ArrayList<>(), 11, 1, 5);
        assertThat(r.getPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("of() total=0 时 pages=0")
    void of_emptyTotal() {
        PageResult<String> r = PageResult.of(new ArrayList<>(), 0, 1, 10);
        assertThat(r.getTotal()).isZero();
        assertThat(r.getPages()).isZero();
    }
}
