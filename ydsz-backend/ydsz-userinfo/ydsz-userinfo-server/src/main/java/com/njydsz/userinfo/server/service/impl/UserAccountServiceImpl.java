package com.njydsz.userinfo.server.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.ResetPasswordDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.domain.entity.UserRoleDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.server.service.UserAccountService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户账号 Service 实现。
 *
 * <p>核心能力：
 * <ul>
 *   <li>密码加密（BCrypt）</li>
 *   <li>用户名唯一性校验</li>
 *   <li>DTO→DO 转换，VO 隔离敏感字段</li>
 *   <li>分页查询</li>
 *   <li>修改密码/重置密码</li>
 *   <li>用户角色分配</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserAccountVO getById(String id) {
        UserAccountDO entity = userAccountMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public Page<UserAccountVO> page(UserAccountPageQueryDTO query) {
        Page<UserAccountDO> page = new Page<>(
                query.getSafePageNum(), query.getSafePageSize());
        LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccountDO::getDeleted, 0);

        if (query.getUsername() != null && !query.getUsername().isBlank()) {
            wrapper.like(UserAccountDO::getUsername, query.getUsername());
        }
        if (query.getRealName() != null && !query.getRealName().isBlank()) {
            wrapper.like(UserAccountDO::getRealName, query.getRealName());
        }
        if (query.getPhone() != null && !query.getPhone().isBlank()) {
            wrapper.like(UserAccountDO::getPhone, query.getPhone());
        }
        if (query.getEmail() != null && !query.getEmail().isBlank()) {
            wrapper.like(UserAccountDO::getEmail, query.getEmail());
        }
        if (query.getStatus() != null) {
            wrapper.eq(UserAccountDO::getStatus, query.getStatus());
        }
        if (query.getUserType() != null && !query.getUserType().isBlank()) {
            wrapper.eq(UserAccountDO::getUserType, query.getUserType());
        }
        if (query.getCompanyId() != null && !query.getCompanyId().isBlank()) {
            wrapper.eq(UserAccountDO::getCompanyId, query.getCompanyId());
        }
        wrapper.orderByDesc(UserAccountDO::getCreatedAt);

        Page<UserAccountDO> result = userAccountMapper.selectPage(page, wrapper);
        Page<UserAccountVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<UserAccountVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<UserAccountVO> list() {
        LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccountDO::getDeleted, 0);
        wrapper.orderByDesc(UserAccountDO::getCreatedAt);
        return userAccountMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(UserAccountCreateDTO dto) {
        LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccountDO::getUsername, dto.getUsername());
        wrapper.eq(UserAccountDO::getDeleted, 0);
        if (userAccountMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.USERNAME_DUPLICATE);
        }

        UserAccountDO entity = new UserAccountDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        entity.setStatus(1);
        entity.setLoginFailCount(0);
        if (dto.getTenantId() == null || dto.getTenantId().isBlank()) {
            entity.setTenantId("1");
        }
        userAccountMapper.insert(entity);
        log.info("User created: username={}, id={}", entity.getUsername(), entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(UserAccountUpdateDTO dto) {
        UserAccountDO entity = userAccountMapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return userAccountMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        UserAccountDO entity = userAccountMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        return userAccountMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(ChangePasswordDTO dto) {
        UserAccountDO entity = userAccountMapper.selectById(dto.getUserId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), entity.getPassword())) {
            throw new BusinessException(UserInfoResultCode.OLD_PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(dto.getNewPassword(), entity.getPassword())) {
            throw new BusinessException(UserInfoResultCode.PASSWORD_SAME_AS_OLD);
        }
        entity.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        return userAccountMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(ResetPasswordDTO dto) {
        UserAccountDO entity = userAccountMapper.selectById(dto.getUserId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }
        entity.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        entity.setLoginFailCount(0);
        entity.setLockedUntil(null);
        return userAccountMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignRoles(String userId, List<String> roleIds) {
        UserAccountDO entity = userAccountMapper.selectById(userId);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.USER_NOT_FOUND);
        }

        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRoleDO::getUserId, userId);
        wrapper.eq(UserRoleDO::getDeleted, 0);
        userRoleMapper.delete(wrapper);

        for (String roleId : roleIds) {
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setTenantId(entity.getTenantId());
            userRoleMapper.insert(ur);
        }
        log.info("Roles assigned to user {}: {}", userId, roleIds);
        return true;
    }

    @Override
    public List<String> getUserRoleIds(String userId) {
        LambdaQueryWrapper<UserRoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRoleDO::getUserId, userId);
        wrapper.eq(UserRoleDO::getDeleted, 0);
        return userRoleMapper.selectList(wrapper).stream()
                .map(UserRoleDO::getRoleId)
                .collect(Collectors.toList());
    }

    private UserAccountVO toVO(UserAccountDO entity) {
        UserAccountVO vo = new UserAccountVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
