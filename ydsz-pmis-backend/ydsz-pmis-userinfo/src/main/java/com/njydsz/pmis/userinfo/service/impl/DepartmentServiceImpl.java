package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.DepartmentFormDTO;
import com.njydsz.pmis.userinfo.entity.DepartmentDO;
import com.njydsz.pmis.userinfo.mapper.DepartmentMapper;
import com.njydsz.pmis.userinfo.service.DepartmentService;
import com.njydsz.pmis.userinfo.vo.DepartmentTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 部门服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentTreeVO> tree() {
        List<DepartmentDO> all = departmentMapper.selectAllEnabled();
        Map<Long, DepartmentTreeVO> map = new HashMap<>();
        for (DepartmentDO d : all) {
            map.put(d.getId(), DepartmentTreeVO.of(d));
        }
        List<DepartmentTreeVO> roots = new ArrayList<>();
        for (DepartmentDO d : all) {
            DepartmentTreeVO node = map.get(d.getId());
            if (d.getParentId() == null || d.getParentId() == 0L) {
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
    @Transactional(readOnly = true)
    public List<DepartmentDO> listAllEnabled() {
        return departmentMapper.selectAllEnabled();
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentDO getById(Long id) {
        DepartmentDO d = departmentMapper.selectById(id);
        if (d == null) {
            throw new BizException(BizErrorCode.DEPARTMENT_NOT_FOUND);
        }
        return d;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DepartmentFormDTO dto) {
        // 编码唯一
        DepartmentDO exists = departmentMapper.selectByCode(dto.getDeptCode());
        if (exists != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.user.msg_58b44529" + dto.getDeptCode());
        }
        // 父部门校验
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId != 0L) {
            DepartmentDO parent = departmentMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException(BizErrorCode.DEPARTMENT_NOT_FOUND, "error.user.msg_b2cadf60");
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
        if (parentId == 0L) {
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
    public void update(DepartmentFormDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_c04220b1");
        }
        DepartmentDO exists = departmentMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.DEPARTMENT_NOT_FOUND);
        }
        // 不允许将父部门改为自身或子部门
        if (dto.getParentId() != null && Objects.equals(dto.getParentId(), dto.getId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_abd06050");
        }
        DepartmentDO entity = new DepartmentDO();
        BeanUtils.copyProperties(dto, entity);
        departmentMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DepartmentDO d = departmentMapper.selectById(id);
        if (d == null) {
            throw new BizException(BizErrorCode.DEPARTMENT_NOT_FOUND);
        }
        // 子部门校验
        List<DepartmentDO> children = departmentMapper.selectByParentId(id);
        if (!children.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_6b5e31bd");
        }
        departmentMapper.deleteById(id);
    }
}