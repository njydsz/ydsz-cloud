package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.entity.Variable;
import com.njydsz.system.infra.mapper.VariableMapper;
import com.njydsz.system.domain.repository.VariableRepository;

/**
 * 系统变量仓储实现（Infra 层）。
 *
 * <p>实现 {@link VariableRepository} 接口，封装 {@link VariableMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>返回领域实体，由 Service 层负责转换为 VO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class VariableRepositoryImpl implements VariableRepository {

  private final VariableMapper variableMapper;

  @Override
  public Optional<Variable> findEnabledByKey(String variableKey) {
    return Optional.ofNullable(
        variableMapper.selectOne(
            new QueryWrapper<Variable>()
                .eq("variable_key", variableKey)
                .eq("status", STATUS_ENABLED)
                .last("LIMIT 1")));
  }

  @Override
  public Optional<Variable> findByKeyIgnoreStatus(String variableKey) {
    return Optional.ofNullable(
        variableMapper.selectOne(
            new QueryWrapper<Variable>()
                .eq("variable_key", variableKey)
                .eq("deleted", 0)
                .last("LIMIT 1")));
  }

  @Override
  public Optional<Variable> findById(String id) {
    return Optional.ofNullable(variableMapper.selectById(id));
  }

  @Override
  public IPage<Variable> findByPage(Page<Variable> page, String variableKey, String status) {
    QueryWrapper<Variable> wrapper = new QueryWrapper<>();
    if (variableKey != null && !variableKey.isBlank()) {
      wrapper.like("variable_key", variableKey);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("created_at");
    return variableMapper.selectPage(page, wrapper);
  }

  @Override
  public List<Variable> findAll() {
    return variableMapper.selectList(null);
  }

  @Override
  public List<Variable> findList(QueryWrapper<Variable> wrapper) {
    return variableMapper.selectList(wrapper);
  }

  @Override
  public boolean insert(Variable entity) {
    return variableMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(Variable entity) {
    return variableMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return variableMapper.deleteById(id) > 0;
  }

  @Override
  public List<Variable> findByTenantId(String tenantId) {
    QueryWrapper<Variable> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (tenantId != null && !tenantId.isBlank()) {
      wrapper.eq("tenant_id", tenantId);
    }
    return variableMapper.selectList(wrapper);
  }
}
