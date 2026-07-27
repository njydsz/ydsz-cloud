package com.njydsz.userinfo.server.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.DepartmentSaveDTO;
import com.njydsz.userinfo.domain.entity.DepartmentDO;
import com.njydsz.userinfo.domain.entity.UserDeptDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.infra.mapper.DepartmentMapper;
import com.njydsz.userinfo.infra.mapper.UserDeptMapper;
import com.njydsz.userinfo.server.service.DepartmentService;
import com.njydsz.common.domain.tree.TreeBuilder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 部门 Service 实现。
 *
 * <p>核心能力：部门 CRUD、编码唯一性校验、子部门检查、人员检查、树形结构构建。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    /** 部门 Mapper */
    private final DepartmentMapper departmentMapper;
    /** 用户-部门关联 Mapper（用于删除前检查是否有人员关联） */
    private final UserDeptMapper userDeptMapper;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException 当部门不存在或已删除时抛出
     */
    @Override
    public DepartmentVO getById(String id) {
        DepartmentDO entity = departmentMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_NOT_FOUND);
        }
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部未删除部门列表（按 sortOrder 降序）
     */
    @Override
    public List<DepartmentVO> list() {
        LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartmentDO::getDeleted, 0);
        wrapper.orderByDesc(DepartmentDO::getSortOrder);
        return departmentMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行 deptCode 唯一性校验后插入，status 默认 ENABLED，parentId 为空时默认 "0"（根节点）。
     *
     * @throws BusinessException 当 deptCode 已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(DepartmentSaveDTO dto) {
        LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartmentDO::getDeptCode, dto.getDeptCode());
        wrapper.eq(DepartmentDO::getDeleted, 0);
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_CODE_DUPLICATE);
        }

        DepartmentDO entity = new DepartmentDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        if (entity.getParentId() == null || entity.getParentId().isBlank()) {
            entity.setParentId("0");
        }
        departmentMapper.insert(entity);
        log.info("Department created: code={}, id={}", entity.getDeptCode(), entity.getId());
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>使用 BeanUtils.copyProperties 更新字段，排除 id。
     *
     * @throws BusinessException 当部门不存在或已删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(DepartmentSaveDTO dto) {
        DepartmentDO entity = departmentMapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return departmentMapper.updateById(entity) > 0;
    }

    /**
     * {@inheritDoc}
     * <p>删除前检查：有子部门不可删除、有人员关联不可删除。
     *
     * @throws BusinessException 当部门不存在、有子部门、或仍有人员关联时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        DepartmentDO entity = departmentMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_NOT_FOUND);
        }

        LambdaQueryWrapper<DepartmentDO> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(DepartmentDO::getParentId, id);
        childWrapper.eq(DepartmentDO::getDeleted, 0);
        if (departmentMapper.selectCount(childWrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_HAS_CHILDREN);
        }

        LambdaQueryWrapper<UserDeptDO> udWrapper = new LambdaQueryWrapper<>();
        udWrapper.eq(UserDeptDO::getDeptId, id);
        udWrapper.eq(UserDeptDO::getDeleted, 0);
        if (userDeptMapper.selectCount(udWrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_HAS_USERS);
        }

        return departmentMapper.deleteById(id) > 0;
    }

    /**
     * {@inheritDoc}
     * <p>查询全部未删除部门，通过 {@link TreeBuilder#buildSimple} 构建树形结构。
     *
     * @return 部门树形结构列表，空数据返回空列表
     */
    @Override
    public List<DepartmentTreeVO> tree() {
        List<DepartmentDO> all = departmentMapper.selectList(
                new LambdaQueryWrapper<DepartmentDO>()
                        .eq(DepartmentDO::getDeleted, 0));
        if (all.isEmpty()) {
            return List.of();
        }

        List<DepartmentTreeVO> voList = all.stream().map(dept -> {
            DepartmentTreeVO vo = new DepartmentTreeVO();
            BeanUtils.copyProperties(dept, vo);
            return vo;
        }).collect(Collectors.toList());

        return TreeBuilder.buildSimple(voList,
                DepartmentTreeVO::getId,
                DepartmentTreeVO::getParentId,
                DepartmentTreeVO::setChildren,
                DepartmentTreeVO::getSortOrder);
    }

    /**
     * 按部门 ID 查询部门负责人。
     *
     * <p>实现：直接读 ydsz_department.leader_id 字段。部门不存在或逻辑删除时返回 null。
     */
    @Override
    public String getDeptLeaderByDeptId(String deptId) {
        if (deptId == null || deptId.isBlank()) {
            return null;
        }
        DepartmentDO entity = departmentMapper.selectById(deptId);
        if (entity == null || entity.getDeleted() == 1) {
            return null;
        }
        return entity.getLeaderId();
    }

    /**
     * 按部门编码查询部门负责人。
     *
     * <p>实现：按 dept_code 查 ydsz_department 后取 leader_id。
     */
    @Override
    public String getDeptLeaderByDeptCode(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartmentDO::getDeptCode, deptCode);
        wrapper.eq(DepartmentDO::getDeleted, 0);
        wrapper.last("LIMIT 1");
        DepartmentDO entity = departmentMapper.selectOne(wrapper);
        if (entity == null) {
            return null;
        }
        return entity.getLeaderId();
    }

    /**
     * 批量查询部门 ID → 部门名映射。
     *
     * <p>实现：{@link com.baomidou.mybatisplus.core.mapper.BaseMapper#selectBatchIds(Collection)}
     * 单条 SQL 完成（已自动追加 {@code deleted = 0} 条件，因 {@link DepartmentDO#getDeleted()} 标注了 {@link com.baomidou.mybatisplus.annotation.TableLogic}）。
     */
    @Override
    public Map<String, String> batchNamesByIds(Collection<String> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctIds = deptIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DepartmentDO> depts = departmentMapper.selectBatchIds(distinctIds);
        Map<String, String> result = new LinkedHashMap<>(depts.size());
        for (DepartmentDO dept : depts) {
            if (dept.getDeptName() != null && !dept.getDeptName().isBlank()) {
                result.put(dept.getId(), dept.getDeptName());
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
    private DepartmentVO toVO(DepartmentDO entity) {
        DepartmentVO vo = new DepartmentVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
