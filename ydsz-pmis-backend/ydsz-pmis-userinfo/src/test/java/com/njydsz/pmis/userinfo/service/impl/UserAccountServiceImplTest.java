package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.PasswordPolicy;
import com.njydsz.pmis.common.service.BloomFilterService;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.userinfo.dto.UserQueryDTO;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;
import com.njydsz.pmis.userinfo.entity.UserRoleDO;
import com.njydsz.pmis.userinfo.entity.RoleDO;
import com.njydsz.pmis.userinfo.mapper.UserAccountMapper;
import com.njydsz.pmis.userinfo.mapper.UserRoleMapper;
import com.njydsz.pmis.userinfo.mapper.User2FAMapper;
import com.njydsz.pmis.userinfo.service.DepartmentService;
import com.njydsz.pmis.userinfo.service.RoleService;
import com.njydsz.pmis.userinfo.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
    private RoleService roleService;
    @Mock
    private DepartmentService departmentService;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private BloomFilterService bloomFilterService;

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

        when(bloomFilterService.mightContain("user:username", "admin")).thenReturn(true);
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

        RoleDO superAdminRole = new RoleDO();
        superAdminRole.setRoleCode("SUPER_ADMIN");
        when(roleService.listByUserId(1L)).thenReturn(List.of(superAdminRole));

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
    @SuppressWarnings("unchecked")
    void create_duplicateUsername_shouldThrowException() {
        UserAccountDO existing = new UserAccountDO();
        existing.setUsername("testuser");

        when(bloomFilterService.mightContain("user:username", "testuser")).thenReturn(true);
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

    // ==================== P0-C1 BCrypt 密码哈希迁移测试 ====================

    @Test
    @DisplayName("创建用户 - 密码应使用 BCrypt 哈希且 salt 为空")
    void create_shouldUseBCryptHash() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class);
             MockedStatic<PasswordPolicy> policy = mockStatic(PasswordPolicy.class)) {
            // 布隆过滤器判定不存在（新用户）
            when(bloomFilterService.mightContain("user:username", "newuser")).thenReturn(false);
            // 密码策略通过
            PasswordPolicy.PasswordCheckResult passResult = mock(PasswordPolicy.PasswordCheckResult.class);
            when(passResult.pass()).thenReturn(true);
            policy.when(() -> PasswordPolicy.check(anyString(), anyString())).thenReturn(passResult);
            // BCrypt 哈希 mock
            cryptoUtil.when(() -> CryptoUtil.hashPasswordBCrypt("Test@123456")).thenReturn("$2a$12$mockedHash");

            UserAccountDO newUser = new UserAccountDO();
            newUser.setUsername("newuser");

            userAccountService.create(newUser, "Test@123456");

            // 验证密码字段为 BCrypt 哈希，salt 为空
            assertEquals("$2a$12$mockedHash", newUser.getPassword());
            assertEquals("", newUser.getSalt());
            verify(userAccountMapper).insert(newUser);
        }
    }

    @Test
    @DisplayName("重置密码 - 密码应使用 BCrypt 哈希且 salt 为空")
    void resetPassword_shouldUseBCryptHash() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class);
             MockedStatic<PasswordPolicy> policy = mockStatic(PasswordPolicy.class)) {
            UserAccountDO existing = new UserAccountDO();
            existing.setId(10L);
            existing.setUsername("resetuser");
            when(userAccountMapper.selectById(10L)).thenReturn(existing);
            // 密码策略通过
            PasswordPolicy.PasswordCheckResult passResult = mock(PasswordPolicy.PasswordCheckResult.class);
            when(passResult.pass()).thenReturn(true);
            policy.when(() -> PasswordPolicy.check(anyString(), anyString())).thenReturn(passResult);
            // BCrypt 哈希 mock
            cryptoUtil.when(() -> CryptoUtil.hashPasswordBCrypt("NewPwd@2026")).thenReturn("$2a$12$newHash");

            userAccountService.resetPassword(10L, "NewPwd@2026");

            assertEquals("$2a$12$newHash", existing.getPassword());
            assertEquals("", existing.getSalt());
            assertEquals(0, existing.getLoginFailCount());
            assertNull(existing.getLockedUntil());
            verify(userAccountMapper).updateById(existing);
        }
    }

    @Test
    @DisplayName("修改密码 - 历史 MD5 密码校验通过后新密码升级为 BCrypt")
    @SuppressWarnings("deprecation")
    void changePassword_legacyMD5_shouldUpgradeToBCrypt() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class);
             MockedStatic<PasswordPolicy> policy = mockStatic(PasswordPolicy.class)) {
            UserAccountDO existing = new UserAccountDO();
            existing.setId(20L);
            existing.setUsername("legacyuser");
            existing.setPassword("md5hash");
            existing.setSalt("legacysalt");
            when(userAccountMapper.selectById(20L)).thenReturn(existing);
            // MD5 格式
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("md5hash")).thenReturn(false);
            cryptoUtil.when(() -> CryptoUtil.verifyPassword("oldPwd", "md5hash", "legacysalt")).thenReturn(true);
            // 新密码策略通过
            PasswordPolicy.PasswordCheckResult passResult = mock(PasswordPolicy.PasswordCheckResult.class);
            when(passResult.pass()).thenReturn(true);
            policy.when(() -> PasswordPolicy.check(anyString(), anyString())).thenReturn(passResult);
            // 新密码 BCrypt 哈希 mock
            cryptoUtil.when(() -> CryptoUtil.hashPasswordBCrypt("NewPwd@2026")).thenReturn("$2a$12$upgradedHash");

            userAccountService.changePassword(20L, "oldPwd", "NewPwd@2026");

            assertEquals("$2a$12$upgradedHash", existing.getPassword());
            assertEquals("", existing.getSalt());
            verify(userAccountMapper).updateById(existing);
        }
    }

    @Test
    @DisplayName("修改密码 - BCrypt 密码校验通过后新密码仍使用 BCrypt")
    @SuppressWarnings("deprecation")
    void changePassword_bcrypt_shouldKeepBCrypt() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class);
             MockedStatic<PasswordPolicy> policy = mockStatic(PasswordPolicy.class)) {
            UserAccountDO existing = new UserAccountDO();
            existing.setId(21L);
            existing.setUsername("bcryptuser");
            existing.setPassword("$2a$12$oldBcryptHash");
            existing.setSalt("");
            when(userAccountMapper.selectById(21L)).thenReturn(existing);
            // BCrypt 格式
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("$2a$12$oldBcryptHash")).thenReturn(true);
            cryptoUtil.when(() -> CryptoUtil.verifyPasswordBCrypt("oldPwd", "$2a$12$oldBcryptHash")).thenReturn(true);
            // 新密码策略通过
            PasswordPolicy.PasswordCheckResult passResult = mock(PasswordPolicy.PasswordCheckResult.class);
            when(passResult.pass()).thenReturn(true);
            policy.when(() -> PasswordPolicy.check(anyString(), anyString())).thenReturn(passResult);
            // 新密码 BCrypt 哈希 mock
            cryptoUtil.when(() -> CryptoUtil.hashPasswordBCrypt("NewPwd@2026")).thenReturn("$2a$12$newBcryptHash");

            userAccountService.changePassword(21L, "oldPwd", "NewPwd@2026");

            assertEquals("$2a$12$newBcryptHash", existing.getPassword());
            assertEquals("", existing.getSalt());
            // 不应调用 MD5 校验路径
            cryptoUtil.verify(() -> CryptoUtil.verifyPassword(anyString(), anyString(), anyString()), never());
        }
    }

    @Test
    @DisplayName("修改密码 - 旧密码错误应抛出异常")
    void changePassword_wrongOldPassword_shouldThrowException() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class)) {
            UserAccountDO existing = new UserAccountDO();
            existing.setId(22L);
            existing.setUsername("testuser");
            existing.setPassword("$2a$12$someBcryptHash");
            existing.setSalt("");
            when(userAccountMapper.selectById(22L)).thenReturn(existing);
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("$2a$12$someBcryptHash")).thenReturn(true);
            cryptoUtil.when(() -> CryptoUtil.verifyPasswordBCrypt("wrongPwd", "$2a$12$someBcryptHash")).thenReturn(false);

            BizException ex = assertThrows(BizException.class,
                    () -> userAccountService.changePassword(22L, "wrongPwd", "NewPwd@2026"));
            assertEquals(30002, ex.getCode());
        }
    }

    @Test
    @DisplayName("升级密码哈希 - 历史 MD5 应升级为 BCrypt")
    void upgradePasswordHash_legacyMD5_shouldUpgrade() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class)) {
            UserAccountDO existing = new UserAccountDO();
            existing.setId(30L);
            existing.setPassword("md5hash");
            existing.setSalt("legacysalt");
            when(userAccountMapper.selectById(30L)).thenReturn(existing);
            // 新 BCrypt 哈希格式合法
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("$2a$12$newBcryptHash")).thenReturn(true);
            // 当前密码非 BCrypt 格式
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("md5hash")).thenReturn(false);

            userAccountService.upgradePasswordHash(30L, "$2a$12$newBcryptHash");

            assertEquals("$2a$12$newBcryptHash", existing.getPassword());
            assertEquals("", existing.getSalt());
            verify(userAccountMapper).updateById(existing);
        }
    }

    @Test
    @DisplayName("升级密码哈希 - 当前已是 BCrypt 应跳过（避免覆盖）")
    void upgradePasswordHash_alreadyBCrypt_shouldSkip() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class)) {
            UserAccountDO existing = new UserAccountDO();
            existing.setId(31L);
            existing.setPassword("$2a$12$currentBcryptHash");
            existing.setSalt("");
            when(userAccountMapper.selectById(31L)).thenReturn(existing);
            // 新哈希格式合法
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("$2a$12$newBcryptHash")).thenReturn(true);
            // 当前密码也是 BCrypt 格式
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("$2a$12$currentBcryptHash")).thenReturn(true);

            userAccountService.upgradePasswordHash(31L, "$2a$12$newBcryptHash");

            // 不应被覆盖
            assertEquals("$2a$12$currentBcryptHash", existing.getPassword());
            verify(userAccountMapper, never()).updateById(any(UserAccountDO.class));
        }
    }

    @Test
    @DisplayName("升级密码哈希 - 非法参数应跳过")
    void upgradePasswordHash_invalidArgs_shouldSkip() {
        try (MockedStatic<CryptoUtil> cryptoUtil = mockStatic(CryptoUtil.class)) {
            // userId 为 null（短路，不应调用 isBCryptFormat）
            assertDoesNotThrow(() -> userAccountService.upgradePasswordHash(null, "$2a$12$hash"));
            // bcryptHash 非 BCrypt 格式
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("notBcrypt")).thenReturn(false);
            assertDoesNotThrow(() -> userAccountService.upgradePasswordHash(40L, "notBcrypt"));
            // 用户不存在
            when(userAccountMapper.selectById(41L)).thenReturn(null);
            cryptoUtil.when(() -> CryptoUtil.isBCryptFormat("$2a$12$hash")).thenReturn(true);
            assertDoesNotThrow(() -> userAccountService.upgradePasswordHash(41L, "$2a$12$hash"));
            // 非法场景不应触发 updateById
            verify(userAccountMapper, never()).updateById(any(UserAccountDO.class));
        }
    }
}