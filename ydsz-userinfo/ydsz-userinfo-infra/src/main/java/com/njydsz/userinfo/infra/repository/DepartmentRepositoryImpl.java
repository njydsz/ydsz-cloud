package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.DepartmentDTO;
import com.njydsz.userinfo.domain.query.DepartmentPageQuery;
import com.njydsz.userinfo.domain.repository.DepartmentRepository;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.infra.converter.UserInfoOrgConverter;
import com.njydsz.userinfo.infra.entity.Department;
import com.njydsz.userinfo.infra.mapper.DepartmentMapper;

/**
 * 部门 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link DepartmentMapper} 实现部门的数据访问。
 * 所有返回值通过 {@link UserInfoOrgConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class DepartmentRepositoryImpl implements DepartmentRepository {

  private final DepartmentMapper departmentMapper;
  private final UserInfoOrgConverter converter;

  @Override
  public Optional<DepartmentVO> findById(String id) {
    Department entity = departmentMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<DepartmentVO> findByParentId(String parentId) {
    LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Department::getParentId, parentId);
    List<Department> entities = departmentMapper.selectList(wrapper);
    return converter.departmentListToVO(entities);
  }

  @Override
  public Optional<DepartmentVO> findByDeptCode(String deptCode) {
    LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Department::getDeptCode, deptCode);
    Department entity = departmentMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<DepartmentVO>> page(DepartmentPageQuery query) {
    Page<Department> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<Department> wrapper = buildWrapper(query);
    Page<Department> result = departmentMapper.selectPage(page, wrapper);
    List<DepartmentVO> vos = converter.departmentListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<DepartmentVO> list(DepartmentPageQuery query) {
    LambdaQueryWrapper<Department> wrapper = buildWrapper(query);
    List<Department> entities = departmentMapper.selectList(wrapper);
    return converter.departmentListToVO(entities);
  }

  @Override
  public List<DepartmentVO> listByIds(Collection<String> ids) {
    List<Department> entities = departmentMapper.selectBatchIds(ids);
    return converter.departmentListToVO(entities);
  }

  @Override
  public DepartmentVO save(DepartmentDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      Department entity = converter.dtoToEntity(dto);
      departmentMapper.insert(entity);
      return converter.entityToVO(entity);
    } else {
      Department entity = converter.dtoToEntityWithId(dto);
      departmentMapper.updateById(entity);
      return converter.entityToVO(entity);
    }
  }

  @Override
  public boolean deleteById(String id) {
    return departmentMapper.deleteById(id) > 0;
  }

  @Override
  public int deleteByParentId(String parentId) {
    LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Department::getParentId, parentId);
    return departmentMapper.delete(wrapper);
  }

  @Override
  public long countByQuery(DepartmentPageQuery query) {
    LambdaQueryWrapper<Department> wrapper = buildWrapper(query);
    return departmentMapper.selectCount(wrapper);
  }

  private LambdaQueryWrapper<Department> buildWrapper(DepartmentPageQuery query) {
    LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
    if (query.getDeptCode() != null && !query.getDeptCode().isBlank()) {
      wrapper.like(Department::getDeptCode, query.getDeptCode());
    }
    if (query.getDeptName() != null && !query.getDeptName().isBlank()) {
      wrapper.like(Department::getDeptName, query.getDeptName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Department::getStatus, query.getStatus());
    }
    return wrapper;
  }
}
