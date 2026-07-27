package com.njydsz.userinfo.server.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.util.BeanUpdateUtil;

import com.njydsz.userinfo.domain.dto.DepartmentSaveDTO;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.entity.UserDept;
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
import com.njydsz.userinfo.domain.converter.UserInfoConverter;

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
        Department entity = departmentMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_NOT_FOUND);
        }
        return UserInfoConverter.INSTANT.entityToVO(entity);
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部未删除部门列表（按 sortOrder 降序）
     */
    @Override
    public List<DepartmentVO> list() {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Department::getSortOrder);
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
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Department::getDeptCode, dto.getDeptCode());
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_CODE_DUPLICATE);
        }

        Department entity = UserInfoConverter.INSTANT.saveDtoToEntity(dto);
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
     * <p>使用 MapStruct 转换（更新操作暂保留 BeanUtils）
     *
     * @throws BusinessException 当部门不存在或已删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(DepartmentSaveDTO dto) {
        Department entity = departmentMapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_NOT_FOUND);
        }
        BeanUpdateUtil.copyNonNull(dto, entity, "id");
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
        Department entity = departmentMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_NOT_FOUND);
        }

        LambdaQueryWrapper<Department> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(Department::getParentId, id);
        if (departmentMapper.selectCount(childWrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.DEPARTMENT_HAS_CHILDREN);
        }

        LambdaQueryWrapper<UserDept> udWrapper = new LambdaQueryWrapper<>();
        udWrapper.eq(UserDept::getDeptId, id);
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
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>()
                        .eq(Department::getDeleted, 0));
        if (all.isEmpty()) {
            return List.of();
        }

        List<DepartmentTreeVO> voList = UserInfoConverter.INSTANT.departmentTreeListToVO(all);

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
        Department entity = departmentMapper.selectById(deptId);
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
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Department::getDeptCode, deptCode);
        wrapper.last("LIMIT 1");
        Department entity = departmentMapper.selectOne(wrapper);
        if (entity == null) {
            return null;
        }
        return entity.getLeaderId();
    }

    /**
     * 批量查询部门 ID → 部门名映射。
     *
     * <p>实现：{@link com.baomidou.mybatisplus.core.mapper.BaseMapper#selectBatchIds(Collection)}
     * 单条 SQL 完成（已自动追加 {@code deleted = 0} 条件，因 {@link Department#getDeleted()} 标注了 {@link com.baomidou.mybatisplus.annotation.TableLogic}）。
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
        List<Department> depts = departmentMapper.selectBatchIds(distinctIds);
        Map<String, String> result = new LinkedHashMap<>(depts.size());
        for (Department dept : depts) {
            if (dept.getDeptName() != null && !dept.getDeptName().isBlank()) {
                result.put(dept.getId(), dept.getDeptName());
            }
        }
        return result;
    }

    /**
     * 将 DO 转换为 VO，使用 MapStruct 转换器
     *
     * @param entity 数据库实体
     * @return 视图对象
     */
    private DepartmentVO UserInfoConverter.INSTANT.entityToVO(Department entity) {
        return UserInfoConverter.INSTANT.entityToVO(entity);
    }
}
