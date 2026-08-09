package com.njydsz.userinfo.infra.repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.repository.DepartmentRepository;
import com.njydsz.userinfo.infra.mapper.DepartmentMapper;
import com.njydsz.userinfo.infra.mapper.UserDeptMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DepartmentRepository 的 MyBatis-Plus 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DepartmentRepositoryImpl implements DepartmentRepository {

    private final DepartmentMapper departmentMapper;
    private final UserDeptMapper userDeptMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public Optional<Department> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(departmentMapper.selectById(id));
    }

    @Override
    public Optional<Department> findByCode(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Department::getDeptCode, deptCode);
        return Optional.ofNullable(departmentMapper.selectOne(wrapper));
    }

    @Override
    public List<Department> findAllActive() {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        // 假设 deleted = 0 表示有效（config 字段为删除标记）
        wrapper.eq(Department::getDeleted, 0);
        return departmentMapper.selectList(wrapper);
    }

    @Override
    public Department save(Department dept) {
        if (dept == null) {
            throw new IllegalArgumentException("Department entity must not be null");
        }
        if (dept.getId() == null || dept.getId().isBlank()) {
            dept.setId(String.valueOf(snowflakeIdGenerator.nextId()));
            departmentMapper.insert(dept);
        } else {
            departmentMapper.updateById(dept);
        }
        return dept;
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return departmentMapper.deleteById(id) > 0;
    }

    @Override
    public List<Department> findChildren(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Department::getParentId, parentId);
        wrapper.eq(Department::getDeleted, 0);
        return departmentMapper.selectList(wrapper);
    }

    @Override
    public boolean existsByCode(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Department::getDeptCode, deptCode);
        return departmentMapper.exists(wrapper);
    }

    @Override
    public boolean hasChildren(String deptId) {
        if (deptId == null || deptId.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Department::getParentId, deptId);
        wrapper.eq(Department::getDeleted, 0);
        return departmentMapper.exists(wrapper);
    }

    @Override
    public boolean hasUsers(String deptId) {
        if (deptId == null || deptId.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<com.njydsz.userinfo.domain.entity.UserDept> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(com.njydsz.userinfo.domain.entity.UserDept::getDeptId, deptId);
        return userDeptMapper.exists(wrapper);
    }
}
