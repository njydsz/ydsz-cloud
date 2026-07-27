package com.njydsz.userinfo.server.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.njydsz.common.auth.annotation.DataScope;

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

    /** 角色 Mapper */
    private final RoleMapper roleMapper;
    /** 角色-权限关联 Mapper */
    private final RolePermissionMapper rolePermissionMapper;
    /** 用户-角色关联 Mapper（用于删除前检查是否有用户关联） */
    private final UserRoleMapper userRoleMapper;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException 当角色不存在或已删除时抛出
     */
    @Override
    public RoleVO getById(String id) {
        RoleDO entity = roleMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.ROLE_NOT_FOUND);
        }
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>支持按 roleCode/roleName 模糊匹配、status 精确匹配过滤，结果按 sortOrder 升序。
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
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

    /**
     * {@inheritDoc}
     *
     * @return 全部未删除角色列表（按 sortOrder 升序）
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<RoleVO> list() {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getDeleted, 0);
        wrapper.orderByAsc(RoleDO::getSortOrder);
        return roleMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行 roleCode 唯一性校验后插入，status 默认 ENABLED，builtIn 默认 false。
     *
     * @throws BusinessException 当 roleCode 已存在时抛出
     */
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

    /**
     * {@inheritDoc}
     * <p>使用 BeanUtils.copyProperties 更新字段，排除 id 和 builtIn（内置标记不可通过更新修改）。
     *
     * @throws BusinessException 当角色不存在或已删除时抛出
     */
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

    /**
     * {@inheritDoc}
     * <p>删除前检查：内置角色不可删除、有用户关联的角色不可删除。
     * 删除时同时清除角色-权限关联记录。
     *
     * @throws BusinessException 当角色不存在、为内置角色、或仍有用户关联时抛出
     */
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

    /**
     * {@inheritDoc}
     * <p>先删除旧的角色-权限关联，再批量插入新关联（全量覆盖模式）。
     *
     * @throws BusinessException 当角色不存在时抛出
     */
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

    /**
     * {@inheritDoc}
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    @Override
    public List<String> getRolePermissionIds(String roleId) {
        LambdaQueryWrapper<RolePermissionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermissionDO::getRoleId, roleId);
        wrapper.eq(RolePermissionDO::getDeleted, 0);
        return rolePermissionMapper.selectList(wrapper).stream()
                .map(RolePermissionDO::getPermissionId)
                .collect(Collectors.toList());
    }

    /**
     * 批量查询角色 ID → 角色名映射。
     *
     * <p>实现：{@link com.baomidou.mybatisplus.core.mapper.BaseMapper#selectBatchIds(Collection)}
     * 单条 SQL 完成（已自动追加 {@code deleted = 0} 条件，因 {@link RoleDO#getDeleted()} 标注了 {@link com.baomidou.mybatisplus.annotation.TableLogic}）。
     */
    @Override
    public Map<String, String> batchNamesByIds(Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctIds = roleIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RoleDO> roles = roleMapper.selectBatchIds(distinctIds);
        Map<String, String> result = new LinkedHashMap<>(roles.size());
        for (RoleDO role : roles) {
            if (role.getRoleName() != null && !role.getRoleName().isBlank()) {
                result.put(role.getId(), role.getRoleName());
            }
        }
        return result;
    }

    /**
     * 将 DO 转换为 VO，使用 BeanUtils.copyProperties 进行属性拷贝。
     *
     * @param entity 数据库实体
     * @return 视图对象
     */
    private RoleVO toVO(RoleDO entity) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
