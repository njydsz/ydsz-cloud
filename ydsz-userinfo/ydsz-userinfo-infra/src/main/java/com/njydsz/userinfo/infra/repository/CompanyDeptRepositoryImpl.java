package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.CompanyDeptDTO;
import com.njydsz.userinfo.domain.repository.CompanyDeptRepository;
import com.njydsz.userinfo.domain.vo.CompanyDeptVO;
import com.njydsz.userinfo.infra.converter.UserInfoOrgConverter;
import com.njydsz.userinfo.infra.entity.CompanyDept;
import com.njydsz.userinfo.infra.mapper.CompanyDeptMapper;

/**
 * 公司-部门关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link CompanyDeptMapper} 实现公司-部门关联的数据访问。
 * 所有返回值通过 {@link UserInfoOrgConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class CompanyDeptRepositoryImpl implements CompanyDeptRepository {

  private final CompanyDeptMapper companyDeptMapper;
  private final UserInfoOrgConverter converter;

  @Override
  public List<CompanyDeptVO> list() {
    List<CompanyDept> entities = companyDeptMapper.selectList(null);
    return converter.companyDeptListToVO(entities);
  }

  @Override
  public Optional<CompanyDeptVO> findById(String id) {
    CompanyDept entity = companyDeptMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<CompanyDeptVO> findByCompanyId(String companyId) {
    LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDept::getCompanyId, companyId);
    List<CompanyDept> entities = companyDeptMapper.selectList(wrapper);
    return converter.companyDeptListToVO(entities);
  }

  @Override
  public Optional<CompanyDeptVO> findByDeptId(String deptId) {
    LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDept::getDeptId, deptId);
    CompanyDept entity = companyDeptMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<CompanyDeptVO> findByCompanyIdAndDeptId(String companyId, String deptId) {
    LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDept::getCompanyId, companyId);
    wrapper.eq(CompanyDept::getDeptId, deptId);
    CompanyDept entity = companyDeptMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public CompanyDeptVO save(CompanyDeptDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      // 创建场景
      CompanyDept entity = converter.dtoToEntity(dto);
      companyDeptMapper.insert(entity);
      return converter.entityToVO(entity);
    } else {
      // 更新场景
      CompanyDept entity = converter.dtoToEntityWithId(dto);
      companyDeptMapper.updateById(entity);
      return converter.entityToVO(entity);
    }
  }

  @Override
  public int deleteByCompanyId(String companyId) {
    LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDept::getCompanyId, companyId);
    return companyDeptMapper.delete(wrapper);
  }

  @Override
  public int deleteByDeptId(String deptId) {
    LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(CompanyDept::getDeptId, deptId);
    return companyDeptMapper.delete(wrapper);
  }

  @Override
  public boolean deleteById(String id) {
    return companyDeptMapper.deleteById(id) > 0;
  }
}
