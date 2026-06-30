package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.user.entity.UserAccountDO;
import com.njydsz.pmis.user.entity.UserRoleDO;
import com.njydsz.pmis.user.mapper.UserAccountMapper;
import com.njydsz.pmis.user.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAccountServiceImpl 单元测试
 */
@DisplayName("UserAccountServiceImpl 用户服务测试")
class UserAccountServiceImplTest {

    private UserAccountMapper userMapper;
    private UserRoleMapper userRoleMapper;
    private UserAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserAccountMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        service = new UserAccountServiceImpl(userMapper, userRoleMapper);
    }

    @Test
    @DisplayName("create 用户名重复应抛 DUPLICATE_KEY")
    void create_duplicate() {
        when(userMapper.selectOne(any())).thenReturn(account(1L, "admin"));
        UserAccountDO u = new UserAccountDO();
        u.setUsername("admin");
        assertThatThrownBy(() -> service.create(u, "pwd"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 应使用 CryptoUtil 加密密码")
    void create_passwordHash() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(UserAccountDO.class))).thenAnswer(inv -> {
            UserAccountDO u = inv.getArgument(0);
            u.setId(100L);
            return 1;
        });

        UserAccountDO u = new UserAccountDO();
        u.setUsername("new");
        Long id = service.create(u, "secret123");
        assertThat(id).isEqualTo(100L);
        assertThat(u.getPassword()).isNotEqualTo("secret123");
        assertThat(CryptoUtil.verifyPassword("secret123", u.getPassword(), u.getSalt())).isTrue();
    }

    @Test
    @DisplayName("delete 内置 admin 不可删除")
    void delete_admin() {
        when(userMapper.selectById(1L)).thenReturn(account(1L, "admin"));
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("delete 普通用户应级联清除角色")
    void delete_cascade() {
        when(userMapper.selectById(2L)).thenReturn(account(2L, "user1"));
        service.delete(2L);
        verify(userMapper).deleteById(2L);
        verify(userRoleMapper, times(1)).delete(any());
    }

    @Test
    @DisplayName("update 不应改 username/password")
    void update_protect() {
        when(userMapper.selectById(10L)).thenReturn(account(10L, "u1"));
        UserAccountDO patch = new UserAccountDO();
        patch.setId(10L);
        patch.setUsername("hacker");
        patch.setPassword("hack");
        patch.setStatus("DISABLED");

        service.update(patch);

        org.mockito.ArgumentCaptor<UserAccountDO> cap = org.mockito.ArgumentCaptor.forClass(UserAccountDO.class);
        verify(userMapper).updateById(cap.capture());
        UserAccountDO saved = cap.getValue();
        assertThat(saved.getUsername()).isNull();
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("resetPassword 应重置 loginFailCount")
    void resetPassword() {
        when(userMapper.selectById(5L)).thenReturn(account(5L, "u5"));
        service.resetPassword(5L, "newPwd");
        org.mockito.ArgumentCaptor<UserAccountDO> cap = org.mockito.ArgumentCaptor.forClass(UserAccountDO.class);
        verify(userMapper).updateById(cap.capture());
        UserAccountDO saved = cap.getValue();
        assertThat(saved.getLoginFailCount()).isZero();
        assertThat(saved.getLockedUntil()).isNull();
        assertThat(CryptoUtil.verifyPassword("newPwd", saved.getPassword(), saved.getSalt())).isTrue();
    }

    @Test
    @DisplayName("assignRoles 空列表应仅删除")
    void assignRoles_empty() {
        service.assignRoles(1L, List.of());
        verify(userRoleMapper, times(1)).delete(any());
        verify(userRoleMapper, never()).insert(any(UserRoleDO.class));
    }

    @Test
    @DisplayName("assignRoles 多次应先删后插")
    void assignRoles_replace() {
        service.assignRoles(1L, List.of(10L, 20L));
        verify(userRoleMapper, times(1)).delete(any());
        verify(userRoleMapper, times(2)).insert(any(UserRoleDO.class));
    }
    private UserAccountDO account(Long id, String username) {
        UserAccountDO u = new UserAccountDO();
        u.setId(id);
        u.setUsername(username);
        u.setStatus("ENABLED");
        return u;
    }
}
