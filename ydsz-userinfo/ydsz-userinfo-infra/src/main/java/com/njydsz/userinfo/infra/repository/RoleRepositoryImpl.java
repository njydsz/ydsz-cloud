package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserRole;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RoleRepository 的 MyBatis-Plus 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public Optional<Role> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(roleMapper.selectById(id));
    }

    @Override
    public Optional<Role> findByCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, roleCode);
        return Optional.ofNullable(roleMapper.selectOne(wrapper));
    }

    @Override
    public List<Role> findAll() {
        return roleMapper.selectList(null);
    }

    @Override
    public List<Role> findByCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Role::getRoleCode, roleCodes);
        return roleMapper.selectList(wrapper);
    }

    @Override
    public Role save(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role entity must not be null");
        }
        if (role.getId() == null || role.getId().isBlank()) {
            role.setId(String.valueOf(snowflakeIdGenerator.nextId()));
            roleMapper.insert(role);
        } else {
            roleMapper.updateById(role);
        }
        return role;
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return roleMapper.deleteById(id) > 0;
    }

    @Override
    public boolean existsByCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, roleCode);
        return roleMapper.exists(wrapper);
    }

    @Override
    public boolean hasUsers(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getRoleId, roleId);
        return userRoleMapper.exists(wrapper);
    }
}
