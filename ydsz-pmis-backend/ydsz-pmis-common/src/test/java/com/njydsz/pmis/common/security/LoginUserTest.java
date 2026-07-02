package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LoginUser 登录用户上下文单元测试
 *
 * <p>覆盖超管判定、权限校验与 Builder 全字段填充。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("LoginUser 登录用户测试")
class LoginUserTest {

    @Test
    @DisplayName("isSuperAdmin 含 *:*:* 权限应返回 true")
    void isSuperAdmin_true() {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .username("admin")
                .permissions(List.of("user:list", "*:*:*"))
                .build();
        assertThat(user.isSuperAdmin()).isTrue();
    }

    @Test
    @DisplayName("isSuperAdmin 不含 *:*:* 应返回 false")
    void isSuperAdmin_false() {
        LoginUser user = LoginUser.builder()
                .userId(2L)
                .username("user")
                .permissions(List.of("user:list"))
                .build();
        assertThat(user.isSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("isSuperAdmin permissions 为空应返回 false")
    void isSuperAdmin_null() {
        LoginUser user = LoginUser.builder().userId(3L).build();
        assertThat(user.isSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("hasPermission 超管对任意权限都应返回 true")
    void hasPermission_superAdmin() {
        LoginUser user = LoginUser.builder()
                .permissions(List.of("*:*:*"))
                .build();
        assertThat(user.hasPermission("user:create")).isTrue();
        assertThat(user.hasPermission("any:perm")).isTrue();
    }

    @Test
    @DisplayName("hasPermission 拥有权限应返回 true")
    void hasPermission_true() {
        LoginUser user = LoginUser.builder()
                .permissions(List.of("user:list", "user:create"))
                .build();
        assertThat(user.hasPermission("user:create")).isTrue();
    }

    @Test
    @DisplayName("hasPermission 未拥有应返回 false")
    void hasPermission_false() {
        LoginUser user = LoginUser.builder()
                .permissions(List.of("user:list"))
                .build();
        assertThat(user.hasPermission("user:delete")).isFalse();
    }

    @Test
    @DisplayName("hasPermission permissions 为空应返回 false")
    void hasPermission_null() {
        LoginUser user = LoginUser.builder().build();
        assertThat(user.hasPermission("user:list")).isFalse();
    }

    @Test
    @DisplayName("Builder 应能正确填充所有字段")
    void builder_allFields() {
        LoginUser user = LoginUser.builder()
                .userId(100L)
                .username("zhangsan")
                .realName("张三")
                .deptId(10L)
                .deptName("研发部")
                .levelCode("L8")
                .roles(List.of("ADMIN", "PM"))
                .permissions(List.of("project:create"))
                .dataScope("DEPT")
                .token("tk-123")
                .loginTime(1000L)
                .expireTime(2000L)
                .build();

        assertThat(user.getUserId()).isEqualTo(100L);
        assertThat(user.getUsername()).isEqualTo("zhangsan");
        assertThat(user.getRealName()).isEqualTo("张三");
        assertThat(user.getDeptId()).isEqualTo(10L);
        assertThat(user.getDeptName()).isEqualTo("研发部");
        assertThat(user.getLevelCode()).isEqualTo("L8");
        assertThat(user.getRoles()).containsExactly("ADMIN", "PM");
        assertThat(user.getDataScope()).isEqualTo("DEPT");
        assertThat(user.getToken()).isEqualTo("tk-123");
        assertThat(user.getLoginTime()).isEqualTo(1000L);
        assertThat(user.getExpireTime()).isEqualTo(2000L);
    }
}
