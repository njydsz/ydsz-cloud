package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.DepartmentRepository;
import com.njydsz.userinfo.infra.entity.DepartmentDO;
import com.njydsz.userinfo.infra.mapper.DepartmentMapper;

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
  public DepartmentDO findById(String id) {
    return departmentMapper.selectById(id);
  }

  @Override
  public List<DepartmentDO> findByParentId(String parentId) {
    LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DepartmentDO::getParentId, parentId);
    return departmentMapper.selectList(wrapper);
  }

  @Override
  public DepartmentDO findByDeptCode(String deptCode) {
    LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DepartmentDO::getDeptCode, deptCode);
    return departmentMapper.selectOne(wrapper);
  }

  @Override
  public List<DepartmentDO> list(LambdaQueryWrapper<DepartmentDO> wrapper) {
    return departmentMapper.selectList(wrapper);
  }

  @Override
  public List<DepartmentDO> listByIds(Collection<String> ids) {
    return departmentMapper.selectBatchIds(ids);
  }

  @Override
  public int insert(DepartmentDO entity) {
    return departmentMapper.insert(entity);
  }

  @Override
  public int updateById(DepartmentDO entity) {
    return departmentMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return departmentMapper.deleteById(id);
  }

  @Override
  public int delete(LambdaQueryWrapper<DepartmentDO> wrapper) {
    return departmentMapper.delete(wrapper);
  }

  @Override
  public long count(LambdaQueryWrapper<DepartmentDO> wrapper) {
    return departmentMapper.selectCount(wrapper);
  }
}
