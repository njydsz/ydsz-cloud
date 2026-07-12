package com.njydsz.pmis.userinfo.server.service.impl.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.domain.dto.permission.RoleFormDTO;
import com.njydsz.pmis.userinfo.domain.dto.permission.RoleQueryDTO;
import com.njydsz.pmis.userinfo.domain.entity.permission.RoleDO;
import com.njydsz.pmis.userinfo.domain.entity.permission.RolePermissionDO;
import com.njydsz.pmis.userinfo.infra.mapper.permission.RoleMapper;
import com.njydsz.pmis.userinfo.infra.mapper.permission.RolePermissionMapper;
import com.njydsz.pmis.userinfo.server.service.permission.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    /** 角色缓存名称 */
    public static final String CACHE_NAME = "role";

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<RoleDO> page(RoleQueryDTO query) {
        Page<RoleDO> page = new Page<>(query.getPage(), Math.min(query.getSize(), PageQuery.MAX_SIZE));
        LambdaQueryWrapper<RoleDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            w.and(qw -> qw.like(RoleDO::getRoleCode, query.getKeyword())
                    .or().like(RoleDO::getRoleName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getDataScope())) {
            w.eq(RoleDO::getDataScope, query.getDataScope());
        }
        if (StringUtils.hasText(query.getStatus())) {
            w.eq(RoleDO::getStatus, query.getStatus());
        }
        w.orderByAsc(RoleDO::getSortOrder).orderByDesc(RoleDO::getId);
        return roleMapper.selectPage(page, w);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'listAllEnabled'", unless = "#result == null || #BaseResponse.isEmpty()")
    public List<RoleDO> listAllEnabled() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getStatus, "ENABLED")
                .orderByAsc(RoleDO::getSortOrder));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#id", unless = "#result == null")
    public RoleDO getById(String id) {
        RoleDO r = roleMapper.selectById(id);
        if (r == null) {
            throw new BizException(StandardResultCode.NOT_FOUND, "error.user.msg_c3f70e4c");
        }
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'byUserId:' + #userId", unless = "#result == null || #BaseResponse.isEmpty()")
    public List<RoleDO> listByUserId(String userId) {
        return roleMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public String create(RoleFormDTO dto) {
        if (roleMapper.selectByCode(dto.getRoleCode()) != null) {
            throw new BizException(StandardResultCode.DUPLICATE_KEY, "error.user.msg_af20e82e");
        }
        RoleDO entity = new RoleDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus("ENABLED");
        if (entity.getDataScope() == null) entity.setDataScope("SELF");
        roleMapper.insert(entity);
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            assignPermissions(entity.getId(), dto.getPermissionIds());
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void update(RoleFormDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.user.msg_6fe5914e");
        }
        RoleDO exists = roleMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BizException(StandardResultCode.NOT_FOUND, "error.user.msg_c3f70e4c");
        }
        RoleDO entity = new RoleDO();
        BeanUtils.copyProperties(dto, entity);
        roleMapper.updateById(entity);
        if (dto.getPermissionIds() != null) {
            assignPermissions(entity.getId(), dto.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void delete(String id) {
        if (roleMapper.selectById(id) == null) {
            throw new BizException(StandardResultCode.NOT_FOUND, "error.user.msg_c3f70e4c");
        }
        // 不允许删除 SUPER_ADMIN
        RoleDO r = roleMapper.selectById(id);
        if ("SUPER_ADMIN".equals(r.getRoleCode())) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.user.msg_5201576b");
        }
        roleMapper.deleteById(id);
        // 清除角色权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>()
                .eq(RolePermissionDO::getRoleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String roleId, List<String> permissionIds) {
        // 先清后插
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>()
                .eq(RolePermissionDO::getRoleId, roleId));
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        for (String pid : permissionIds) {
            RolePermissionDO rp = new RolePermissionDO();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'permIds:' + #roleId", unless = "#result == null || #BaseResponse.isEmpty()")
    public List<String> listPermissionIds(String roleId) {
        return rolePermissionMapper.selectPermissionIdsByRoleId(roleId);
    }
}