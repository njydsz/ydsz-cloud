package com.njydsz.userinfo.server.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.RolePageQueryDTO;
import com.njydsz.userinfo.domain.dto.RoleSaveDTO;
import com.njydsz.userinfo.domain.entity.RoleDO;
import com.njydsz.userinfo.domain.entity.RolePermissionDO;
import com.njydsz.userinfo.domain.entity.UserRoleDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.RolePermissionMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;
import com.njydsz.userinfo.server.service.RoleService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 角色 Service 实现。
 *
 * <p>核心能力：角色 CRUD、唯一性校验、内置角色保护、角色-权限批量分配。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public RoleVO getById(String id) {
        RoleDO entity = roleMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.ROLE_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public Page<RoleVO> page(RolePageQueryDTO query) {
        Page<RoleDO> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getDeleted, 0);
        if (query.getRoleCode() != null && !query.getRoleCode().isBlank()) {
            wrapper.like(RoleDO::getRoleCode, query.getRoleCode());
        }
        if (query.getRoleName() != null && !query.getRoleName().isBlank()) {
            wrapper.like(RoleDO::getRoleName, query.getRoleName());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(RoleDO::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(RoleDO::getSortOrder);
        Page<RoleDO> result = roleMapper.selectPage(page, wrapper);
        Page<RoleVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<RoleVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<RoleVO> list() {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getDeleted, 0);
        wrapper.orderByAsc(RoleDO::getSortOrder);
        return roleMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(RoleSaveDTO dto) {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getRoleCode, dto.getRoleCode());
        wrapper.eq(RoleDO::getDeleted, 0);
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.ROLE_CODE_DUPLICATE);
        }

        RoleDO entity = new RoleDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        if (entity.getBuiltIn() == null) {
            entity.setBuiltIn(false);
        }
        roleMapper.insert(entity);
        log.info("Role created: code={}, id={}", entity.getRoleCode(), entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(RoleSaveDTO dto) {
        RoleDO entity = roleMapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.ROLE_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id", "builtIn");
        return roleMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        RoleDO entity = roleMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(UserInfoResultCode.ROLE_BUILTIN_CANNOT_DELETE);
        }

        LambdaQueryWrapper<UserRoleDO> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRoleDO::getRoleId, id);
        urWrapper.eq(UserRoleDO::getDeleted, 0);
        if (userRoleMapper.selectCount(urWrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.ROLE_HAS_USERS);
        }

        LambdaQueryWrapper<RolePermissionDO> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.eq(RolePermissionDO::getRoleId, id);
        rpWrapper.eq(RolePermissionDO::getDeleted, 0);
        rolePermissionMapper.delete(rpWrapper);

        return roleMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignPermissions(String roleId, List<String> permissionIds) {
        RoleDO role = roleMapper.selectById(roleId);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.ROLE_NOT_FOUND);
        }

        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, roleId);
        wrapper.eq(RolePermissionDO::getDeleted, 0);
        rolePermissionMapper.delete(wrapper);

        // 批量插入（替代 N+1 循环）
        List<RolePermissionDO> list = new ArrayList<>(permissionIds.size());
        for (String permId : permissionIds) {
            RolePermissionDO rp = new RolePermissionDO();
            rp.setId(IdWorker.getIdStr());
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            rp.setTenantId(role.getTenantId());
            list.add(rp);
        }
        if (!list.isEmpty()) {
            rolePermissionMapper.batchInsert(list);
        }
        log.info("Permissions assigned to role {}: {}", roleId, permissionIds.size());
        return true;
    }

    @Override
    public List<String> getRolePermissionIds(String roleId) {
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, roleId);
        wrapper.eq(RolePermissionDO::getDeleted, 0);
        return rolePermissionMapper.selectList(wrapper).stream()
                .map(RolePermissionDO::getPermissionId)
                .collect(Collectors.toList());
    }

    private RoleVO toVO(RoleDO entity) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
