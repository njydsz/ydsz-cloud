package com.njydsz.pmis.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.CryptoUtil;
import com.njydsz.pmis.user.dto.UserQueryDTO;
import com.njydsz.pmis.user.entity.UserAccountDO;
import com.njydsz.pmis.user.entity.UserRoleDO;
import com.njydsz.pmis.user.mapper.UserAccountMapper;
import com.njydsz.pmis.user.mapper.UserRoleMapper;
import com.njydsz.pmis.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户账号服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public UserAccountDO findByUsername(String username) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountDO>()
                .eq(UserAccountDO::getUsername, username));
    }

    @Override
    public UserAccountDO findById(Long userId) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        return u;
    }

    @Override
    public Page<UserAccountDO> page(UserQueryDTO query) {
        Page<UserAccountDO> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<UserAccountDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            w.like(UserAccountDO::getUsername, query.getKeyword());
        }
        if (StringUtils.hasText(query.getStatus())) {
            w.eq(UserAccountDO::getStatus, query.getStatus());
        }
        if (query.getEmployeeId() != null) {
            w.eq(UserAccountDO::getEmployeeId, query.getEmployeeId());
        }
        w.orderByDesc(UserAccountDO::getId);
        return userAccountMapper.selectPage(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserAccountDO user, String rawPassword) {
        if (findByUsername(user.getUsername()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "用户名已存在");
        }
        String[] pair = CryptoUtil.encryptPassword(rawPassword);
        user.setPassword(pair[0]);
        user.setSalt(pair[1]);
        if (user.getStatus() == null) user.setStatus("ENABLED");
        user.setLoginFailCount(0);
        user.setLastLoginTime(null);
        userAccountMapper.insert(user);
        return user.getId();
    }

    @Override
    public void update(UserAccountDO user) {
        if (user.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户 ID 不能为空");
        }
        UserAccountDO exists = userAccountMapper.selectById(user.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        // 不可改用户名/密码
        user.setUsername(null);
        user.setPassword(null);
        user.setSalt(null);
        userAccountMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        if ("admin".equals(u.getUsername())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "内置 admin 不可删除");
        }
        userAccountMapper.deleteById(userId);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        String[] pair = CryptoUtil.encryptPassword(newPassword);
        u.setPassword(pair[0]);
        u.setSalt(pair[1]);
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        userAccountMapper.updateById(u);
        log.info("[User] 重置密码 userId={}", userId);
    }

    @Override
    public void toggleStatus(Long userId, String status) {
        UserAccountDO u = userAccountMapper.selectById(userId);
        if (u == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        u.setStatus(status);
        userAccountMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>()
                .eq(UserRoleDO::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long rid : roleIds) {
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(rid);
            userRoleMapper.insert(ur);
        }
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }
}
