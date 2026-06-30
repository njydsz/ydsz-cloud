package com.njydsz.pmis.common.security;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DataScopeHelper 测试
 *
 * @author ydsz-pmis-team
 */
class DataScopeHelperTest {

    @BeforeEach
    void setUp() {
        SecurityContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    private void setUser(LoginUser u) {
        SecurityContext.setCurrent(u);
    }

    @Test
    @DisplayName("ALL 模式 - 无限制")
    void all_unrestricted() {
        setUser(LoginUser.builder().userId(1L).username("admin")
                .permissions(List.of("*:*:*")).dataScope("ALL").deptId(99L).build());

        DataScopeHelper.requireDept(1L);
        DataScopeHelper.requireDept(2L);
        DataScopeHelper.requireOwner(2L);
        String fragment = DataScopeHelper.buildSqlFragment("t", "t");
        assertThat(fragment).isEmpty();
    }

    @Test
    @DisplayName("SELF 模式 - 越权抛异常")
    void self_forbidden() {
        setUser(LoginUser.builder().userId(1L).username("u")
                .permissions(List.of()).dataScope("SELF").deptId(10L).build());

        assertThatThrownBy(() -> DataScopeHelper.requireOwner(2L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DATA_SCOPE_FORBIDDEN.getCode());

        DataScopeHelper.requireOwner(1L);
    }

    @Test
    @DisplayName("DEPT 模式 - 同部门通过")
    void dept_allow() {
        setUser(LoginUser.builder().userId(1L).username("u")
                .permissions(List.of()).dataScope("DEPT").deptId(10L).build());

        DataScopeHelper.requireDept(10L);
        assertThatThrownBy(() -> DataScopeHelper.requireDept(20L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("CUSTOM 模式 - 自定义部门集")
    void custom() {
        setUser(LoginUser.builder().userId(1L).username("u")
                .permissions(List.of()).dataScope("CUSTOM").deptId(10L)
                .customDeptIds(List.of(10L, 20L, 30L)).build());

        DataScopeHelper.requireDept(20L);
        assertThatThrownBy(() -> DataScopeHelper.requireDept(40L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("buildSqlFragment SELF 模式")
    void buildSqlFragment_self() {
        setUser(LoginUser.builder().userId(7L).username("u")
                .permissions(List.of()).dataScope("SELF").deptId(1L).build());
        String f = DataScopeHelper.buildSqlFragment("t", "t");
        assertThat(f).contains("creator_id").contains("7");
    }

    @Test
    @DisplayName("buildSqlFragment DEPT_AND_CHILD 模式")
    void buildSqlFragment_deptChild() {
        setUser(LoginUser.builder().userId(1L).username("u")
                .permissions(List.of()).dataScope("DEPT_AND_CHILD").deptId(10L)
                .build());
        // 没有下级部门列表时回退到单值
        String f = DataScopeHelper.buildSqlFragment("t", "t");
        assertThat(f).contains("dept_id").contains("10");
    }

    @Test
    @DisplayName("filterDeptIds 越权过滤")
    void filterDeptIds() {
        setUser(LoginUser.builder().userId(1L).username("u")
                .permissions(List.of()).dataScope("CUSTOM").deptId(10L)
                .customDeptIds(List.of(10L, 20L)).build());

        Set<Long> input = Set.of(10L, 20L, 30L, 40L);
        var out = DataScopeHelper.filterDeptIds(input);
        assertThat(out).contains(10L, 20L).doesNotContain(30L, 40L);
    }

    @Test
    @DisplayName("未登录用户默认 SELF 模式")
    void noLogin() {
        // 未设置 SecurityContext
        String f = DataScopeHelper.buildSqlFragment("t", "t");
        assertThat(f).contains("creator_id");
    }
}
