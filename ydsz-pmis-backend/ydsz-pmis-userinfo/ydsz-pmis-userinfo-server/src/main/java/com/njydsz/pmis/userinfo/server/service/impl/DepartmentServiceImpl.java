package com.njydsz.pmis.userinfo.server.service.impl.org;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.jdbc.constant.DataSourceConstants;
import com.njydsz.pmis.userinfo.domain.dto.org.DepartmentFormDTO;
import com.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import com.njydsz.pmis.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.pmis.userinfo.infra.mapper.org.DepartmentMapper;
import com.njydsz.pmis.userinfo.server.service.org.DepartmentService;

import lombok.RequiredArgsConstructor;

/**
 * 部门服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    /** 部门缓存名称 */
    public static final String CACHE_NAME = "dept";

    private final DepartmentMapper departmentMapper;

    @Override
    @DS(DataSourceConstants.SLAVE)
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'tree'", unless = "#result == null || #BaseResponse.isEmpty()")
    public List<DepartmentTreeVO> tree() {
        List<DepartmentDO> all = departmentMapper.selectAllEnabled();
        Map<String, DepartmentTreeVO> map = new HashMap<>();
        for (DepartmentDO d : all) {
            map.put(d.getId(), DepartmentTreeVO.of(d));
        }
        List<DepartmentTreeVO> roots = new ArrayList<>();
        for (DepartmentDO d : all) {
            DepartmentTreeVO node = map.get(d.getId());
            if (d.getParentId() == null || "0".equals(d.getParentId())) {
                roots.add(node);
            } else {
                DepartmentTreeVO parent = map.get(d.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    @Override
    @DS(DataSourceConstants.SLAVE)
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'listAllEnabled'", unless = "#result == null || #BaseResponse.isEmpty()")
    public List<DepartmentDO> listAllEnabled() {
        return departmentMapper.selectAllEnabled();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#id", unless = "#result == null")
    public DepartmentDO getById(String id) {
        DepartmentDO d = departmentMapper.selectById(id);
        if (d == null) {
            throw new SysException(BaseResultCode.DEPARTMENT_NOT_FOUND);
        }
        return d;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public String create(DepartmentFormDTO dto) {
        // 编码唯一
        DepartmentDO exists = departmentMapper.selectByCode(dto.getDeptCode());
        if (exists != null) {
            throw new SysException(BaseResultCode.DUPLICATE_KEY, "error.user.msg_58b44529", dto.getDeptCode());
        }
        // 父部门校验
        String parentId = dto.getParentId() == null ? "0" : dto.getParentId();
        if (!"0".equals(parentId)) {
            DepartmentDO parent = departmentMapper.selectById(parentId);
            if (parent == null) {
                throw new SysException(BaseResultCode.DEPARTMENT_NOT_FOUND, "error.user.msg_b2cadf60");
            }
        }
        DepartmentDO entity = new DepartmentDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setParentId(parentId);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        departmentMapper.insert(entity);
        // 更新部门路径
        if ("0".equals(parentId)) {
            entity.setDeptPath("/" + entity.getId());
        } else {
            DepartmentDO parent = departmentMapper.selectById(parentId);
            entity.setDeptPath(parent.getDeptPath() + "/" + entity.getId());
        }
        departmentMapper.updateById(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void update(DepartmentFormDTO dto) {
        if (dto.getId() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.user.msg_c04220b1");
        }
        DepartmentDO exists = departmentMapper.selectById(dto.getId());
        if (exists == null) {
            throw new SysException(BaseResultCode.DEPARTMENT_NOT_FOUND);
        }
        // 不允许将父部门改为自身或子部门
        if (dto.getParentId() != null && Objects.equals(dto.getParentId(), dto.getId())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.user.msg_abd06050");
        }
        DepartmentDO entity = new DepartmentDO();
        BeanUtils.copyProperties(dto, entity);
        departmentMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void delete(String id) {
        DepartmentDO d = departmentMapper.selectById(id);
        if (d == null) {
            throw new SysException(BaseResultCode.DEPARTMENT_NOT_FOUND);
        }
        // 子部门校验
        List<DepartmentDO> children = departmentMapper.selectByParentId(id);
        if (!children.isEmpty()) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.user.msg_6b5e31bd");
        }
        departmentMapper.deleteById(id);
    }
}