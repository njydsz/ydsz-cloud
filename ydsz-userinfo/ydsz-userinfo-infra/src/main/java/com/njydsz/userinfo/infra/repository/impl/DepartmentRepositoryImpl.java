package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.infra.mapper.DepartmentMapper;
import com.njydsz.userinfo.infra.repository.DepartmentRepository;

/**
 * 部门 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link DepartmentMapper} 实现部门的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class DepartmentRepositoryImpl implements DepartmentRepository {

  private final DepartmentMapper departmentMapper;

  @Override
  public Department findById(String id) {
    return departmentMapper.selectById(id);
  }

  @Override
  public List<Department> findByParentId(String parentId) {
    LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Department::getParentId, parentId);
    return departmentMapper.selectList(wrapper);
  }

  @Override
  public Department findByDeptCode(String deptCode) {
    LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Department::getDeptCode, deptCode);
    return departmentMapper.selectOne(wrapper);
  }

  @Override
  public List<Department> list(LambdaQueryWrapper<Department> wrapper) {
    return departmentMapper.selectList(wrapper);
  }

  @Override
  public List<Department> listByIds(Collection<String> ids) {
    return departmentMapper.selectBatchIds(ids);
  }

  @Override
  public int insert(Department entity) {
    return departmentMapper.insert(entity);
  }

  @Override
  public int updateById(Department entity) {
    return departmentMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return departmentMapper.deleteById(id);
  }

  @Override
  public int delete(LambdaQueryWrapper<Department> wrapper) {
    return departmentMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<Department> wrapper) {
    return departmentMapper.selectCount(wrapper);
  }
}
