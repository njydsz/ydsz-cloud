package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.Variable;
import com.njydsz.system.infra.mapper.VariableMapper;
import com.njydsz.system.domain.repository.VariableRepository;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.query.VariablePageQuery;
import com.njydsz.system.domain.vo.VariableVO;

/**
 * 系统变量仓储实现（Infra 层）。
 *
 * <p>实现 {@link VariableRepository} 接口，封装 {@link VariableMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link SystemConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link SystemConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class VariableRepositoryImpl implements VariableRepository {

  private final VariableMapper variableMapper;

  private final SystemConverter converter;

  @Override
  public Optional<VariableVO> findEnabledByKey(String variableKey) {
    return Optional.ofNullable(
        variableMapper.selectOne(
            new QueryWrapper<Variable>()
                .eq("variable_key", variableKey)
                .eq("status", STATUS_ENABLED)
                .last("LIMIT 1")))
        .map(converter::entityToVO);
  }

  @Override
  public Optional<VariableVO> findByKeyIgnoreStatus(String variableKey) {
    return Optional.ofNullable(
        variableMapper.selectOne(
            new QueryWrapper<Variable>()
                .eq("variable_key", variableKey)
                .eq("deleted", 0)
                .last("LIMIT 1")))
        .map(converter::entityToVO);
  }

  @Override
  public Optional<VariableVO> findById(String id) {
    return Optional.ofNullable(variableMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public IPage<VariableVO> findByPage(VariablePageQuery query) {
    Page<Variable> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<Variable> wrapper = new QueryWrapper<>();
    if (query.getVariableKey() != null && !query.getVariableKey().isBlank()) {
      wrapper.like("variable_key", query.getVariableKey());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.orderByDesc("created_at");
    IPage<Variable> entityPage = variableMapper.selectPage(page, wrapper);
    // DO → VO 转换
    List<VariableVO> vos = converter.variableListToVO(entityPage.getRecords());
    Page<VariableVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    voPage.setRecords(vos);
    return voPage;
  }

  @Override
  public List<VariableVO> findAll() {
    return converter.variableListToVO(variableMapper.selectList(null));
  }

  @Override
  public boolean insert(VariableDTO dto) {
    Variable entity = converter.dtoToEntity(dto);
    return variableMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(VariableDTO dto) {
    Variable entity = converter.dtoToEntityWithId(dto);
    return variableMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return variableMapper.deleteById(id) > 0;
  }

  @Override
  public List<VariableVO> findByTenantId(String tenantId) {
    QueryWrapper<Variable> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (tenantId != null && !tenantId.isBlank()) {
      wrapper.eq("tenant_id", tenantId);
    }
    return converter.variableListToVO(variableMapper.selectList(wrapper));
  }
}
