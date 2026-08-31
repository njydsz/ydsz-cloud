package com.njydsz.system.infra.repository;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.query.VariablePageQuery;
import com.njydsz.system.domain.repository.VariableRepository;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.Variable;
import com.njydsz.system.infra.mapper.VariableMapper;




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
            new LambdaQueryWrapper<Variable>()
                .eq(Variable::getVariableKey, variableKey)
                .eq(Variable::getStatus, STATUS_ENABLED)
                .last("LIMIT 1")))
        .map(converter::entityToVO);
  }

  @Override
  public Optional<VariableVO> findByKeyIgnoreStatus(String variableKey) {
    return Optional.ofNullable(
        variableMapper.selectOne(
            new LambdaQueryWrapper<Variable>()
                .eq(Variable::getVariableKey, variableKey)
                .eq(Variable::getDeleted, 0)
                .last("LIMIT 1")))
        .map(converter::entityToVO);
  }

  @Override
  public Optional<VariableVO> findById(String id) {
    return Optional.ofNullable(variableMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<VariableVO>> findByPage(VariablePageQuery query) {
    Page<Variable> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<Variable> wrapper = new LambdaQueryWrapper<>();
    if (query.getVariableKey() != null && !query.getVariableKey().isBlank()) {
      wrapper.like(Variable::getVariableKey, query.getVariableKey());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Variable::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(Variable::getCreatedAt);
    IPage<Variable> result = variableMapper.selectPage(page, wrapper);
    List<VariableVO> vos = converter.variableListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public List<VariableVO> findAll() {
    return converter.variableListToVO(variableMapper.selectList(null));
  }

  @Override
  public boolean insert(VariableDTO dto) {
    Variable entity = converter.dtoToEntity(dto);
    boolean success = variableMapper.insert(entity) > 0;
    // MyBatis-Plus 回填 snowflake ID 到 entity，需同步回 DTO
    if (success && entity.getId() != null) {
      dto.setId(entity.getId());
    }
    return success;
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
}
