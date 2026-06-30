package com.njydsz.pmis.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.PermissionFormDTO;
import com.njydsz.pmis.user.entity.PermissionDO;
import com.njydsz.pmis.user.mapper.PermissionMapper;
import com.njydsz.pmis.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    @Override
    public List<PermissionDO> listAllEnabled() {
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>()
                .eq(PermissionDO::getStatus, "ENABLED")
                .orderByAsc(PermissionDO::getSortOrder).orderByAsc(PermissionDO::getId));
    }

    @Override
    public List<String> listPermCodesByUserId(Long userId) {
        return permissionMapper.selectPermCodesByUserId(userId);
    }

    @Override
    public List<PermissionDO> listByRoleId(Long roleId) {
        return permissionMapper.selectByRoleId(roleId);
    }

    @Override
    public PermissionDO getById(Long id) {
        PermissionDO p = permissionMapper.selectById(id);
        if (p == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "权限不存在");
        }
        return p;
    }

    @Override
    public Long create(PermissionFormDTO dto) {
        if (permissionMapper.selectByCode(dto.getPermCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "权限编码已存在");
        }
        PermissionDO entity = new PermissionDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus("ENABLED");
        if (entity.getVisible() == null) entity.setVisible(1);
        if (entity.getParentId() == null) entity.setParentId(0L);
        permissionMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(PermissionFormDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "权限 ID 不能为空");
        }
        PermissionDO exists = permissionMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "权限不存在");
        }
        PermissionDO entity = new PermissionDO();
        BeanUtils.copyProperties(dto, entity);
        permissionMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        // 存在子权限则不允许删除
        Long childCount = permissionMapper.selectCount(new LambdaQueryWrapper<PermissionDO>()
                .eq(PermissionDO::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "存在子权限，无法删除");
        }
        permissionMapper.deleteById(id);
    }
}
