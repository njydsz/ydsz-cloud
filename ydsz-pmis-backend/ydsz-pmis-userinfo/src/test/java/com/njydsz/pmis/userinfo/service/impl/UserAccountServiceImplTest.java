package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.UserQueryDTO;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;
import com.njydsz.pmis.userinfo.entity.UserRoleDO;
import com.njydsz.pmis.userinfo.mapper.UserAccountMapper;
import com.njydsz.pmis.userinfo.mapper.UserRoleMapper;
import com.njydsz.pmis.userinfo.mapper.User2FAMapper;
import com.njydsz.pmis.userinfo.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户账号服务测试")
class UserAccountServiceImplTest {

    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private User2FAMapper user2FAMapper;
    @Mock
    private SessionService sessionService;
    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private UserAccountServiceImpl userAccountService;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("根据用户名查询用户")
    void findByUsername_shouldReturnUser() {
        UserAccountDO user = new UserAccountDO();
        user.setId(1L);
        user.setUsername("admin");
        user.setStatus("ENABLED");

        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        UserAccountDO result = userAccountService.findByUsername("admin");
        assertNotNull(result);
        assertEquals("admin", result.getUsername());
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("根据ID查询用户成功")
    void findById_shouldReturnUser() {
        UserAccountDO user = new UserAccountDO();
        user.setId(1L);
        user.setUsername("admin");

        when(userAccountMapper.selectById(1L)).thenReturn(user);

        UserAccountDO result = userAccountService.findById(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("根据ID查询不存在的用户时抛出异常")
    void findById_notFound_shouldThrowException() {
        when(userAccountMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> userAccountService.findById(999L));
        assertEquals(30001, ex.getCode());
    }

    @Test
    @DisplayName("删除admin用户时抛出异常")
    void delete_admin_shouldThrowException() {
        UserAccountDO admin = new UserAccountDO();
        admin.setId(1L);
        admin.setUsername("admin");

        when(userAccountMapper.selectById(1L)).thenReturn(admin);

        BizException ex = assertThrows(BizException.class, () -> userAccountService.delete(1L));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("删除不存在的用户时抛出异常")
    void delete_notFound_shouldThrowException() {
        when(userAccountMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> userAccountService.delete(999L));
        assertEquals(30001, ex.getCode());
    }

    @Test
    @DisplayName("创建用户时用户名重复抛出异常")
    void create_duplicateUsername_shouldThrowException() {
        UserAccountDO existing = new UserAccountDO();
        existing.setUsername("testuser");

        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        UserAccountDO newUser = new UserAccountDO();
        newUser.setUsername("testuser");

        BizException ex = assertThrows(BizException.class,
                () -> userAccountService.create(newUser, "Test@123456"));
        assertEquals(10102, ex.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("分页查询用户")
    void page_shouldReturnPagedResult() {
        UserQueryDTO query = new UserQueryDTO();
        query.setPage(1);
        query.setSize(10);

        Page<UserAccountDO> mockPage = new Page<>(1, 10);
        UserAccountDO user = new UserAccountDO();
        user.setId(1L);
        user.setUsername("testuser");
        mockPage.setRecords(List.of(user));
        mockPage.setTotal(1);

        when(userAccountMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<UserAccountDO> result = userAccountService.page(query);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("为用户分配角色")
    void assignRoles_shouldDeleteAndInsert() {
        List<Long> roleIds = List.of(1L, 2L, 3L);

        userAccountService.assignRoles(1L, roleIds);

        verify(userRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(userRoleMapper, times(3)).insert(any(UserRoleDO.class));
    }

    @Test
    @DisplayName("更新不存在的用户时抛出异常")
    void update_notFound_shouldThrowException() {
        when(userAccountMapper.selectById(999L)).thenReturn(null);

        UserAccountDO user = new UserAccountDO();
        user.setId(999L);

        BizException ex = assertThrows(BizException.class, () -> userAccountService.update(user));
        assertEquals(30001, ex.getCode());
    }
}