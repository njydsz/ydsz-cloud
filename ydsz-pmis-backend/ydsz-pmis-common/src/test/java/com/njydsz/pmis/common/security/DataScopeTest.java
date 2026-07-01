package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataScope 枚举测试
 *
 * @author ydsz-pmis-team
 */
class DataScopeTest {

    @Test
    @DisplayName("parse 合法字符串")
    void parse_valid() {
        assertThat(DataScope.parse("ALL")).isEqualTo(DataScope.ALL);
        assertThat(DataScope.parse("dept")).isEqualTo(DataScope.DEPT);
        assertThat(DataScope.parse("DEPT_AND_CHILD")).isEqualTo(DataScope.DEPT_AND_CHILD);
        assertThat(DataScope.parse("SELF")).isEqualTo(DataScope.SELF);
    }

    @Test
    @DisplayName("parse null/空/非法回退 SELF")
    void parse_invalid() {
        assertThat(DataScope.parse(null)).isEqualTo(DataScope.SELF);
        assertThat(DataScope.parse("")).isEqualTo(DataScope.SELF);
        assertThat(DataScope.parse("  ")).isEqualTo(DataScope.SELF);
        assertThat(DataScope.parse("UNKNOWN")).isEqualTo(DataScope.SELF);
    }

    @Test
    @DisplayName("isCrossDept 仅 ALL 为 true")
    void isCrossDept() {
        assertThat(DataScope.ALL.isCrossDept()).isTrue();
        assertThat(DataScope.DEPT.isCrossDept()).isFalse();
        assertThat(DataScope.SELF.isCrossDept()).isFalse();
        assertThat(DataScope.CUSTOM.isCrossDept()).isFalse();
    }

    @Test
    @DisplayName("DataScopeContext.isAll")
    void context_isAll() {
        DataScopeContext all = DataScopeContext.builder().scope(DataScope.ALL).build();
        assertThat(all.isAll()).isTrue();

        DataScopeContext dept = DataScopeContext.builder().scope(DataScope.DEPT).superAdmin(false).build();
        assertThat(dept.isAll()).isFalse();

        DataScopeContext super_ = DataScopeContext.builder().scope(DataScope.SELF).superAdmin(true).build();
        assertThat(super_.isAll()).isTrue();
    }

    @Test
    @DisplayName("DataScopeContext.from(null) 默认 SELF")
    void context_fromNull() {
        DataScopeContext ctx = DataScopeContext.from(null);
        assertThat(ctx.getScope()).isEqualTo(DataScope.SELF);
        assertThat(ctx.isSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("DataScopeContext.from 超管")
    void context_fromSuper() {
        LoginUser u = LoginUser.builder()
                .userId(1L).username("admin")
                .permissions(List.of("*:*:*"))
                .dataScope("SELF")
                .build();
        DataScopeContext ctx = DataScopeContext.from(u);
        assertThat(ctx.isSuperAdmin()).isTrue();
        assertThat(ctx.isAll()).isTrue();
    }
}
