package com.njydsz.literule.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.literule.domain.converter.LiteruleConverter;
import com.njydsz.literule.domain.dto.DecisionTableDTO;
import com.njydsz.literule.domain.entity.DecisionTable;
import com.njydsz.literule.domain.repository.DecisionTableRepository;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.infra.mapper.DecisionTableMapper;

/**
 * 决策表仓储实现（Infra 层）。
 *
 * <p>实现 {@link DecisionTableRepository} 接口，封装 {@link DecisionTableMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link LiteruleConverter} 将 Entity 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link LiteruleConverter} 转换为 Entity 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class DecisionTableRepositoryImpl implements DecisionTableRepository {

  private final DecisionTableMapper decisionTableMapper;

  private final LiteruleConverter converter = LiteruleConverter.INSTANCE;

  @Override
  public List<DecisionTableVO> findAll() {
    return converter.decisionTableListToVO(decisionTableMapper.selectList(null));
  }

  @Override
  public Optional<DecisionTableVO> findByTableCode(String tableCode) {
    DecisionTable entity =
        decisionTableMapper.selectOne(
            new LambdaQueryWrapper<DecisionTable>().eq(DecisionTable::getTableCode, tableCode));
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public DecisionTableVO save(DecisionTableDTO dto) {
    DecisionTable entity = converter.postDtoToEntity(dto);
    if (entity.getId() != null) {
      decisionTableMapper.updateById(entity);
    } else {
      decisionTableMapper.insert(entity);
    }
    return converter.entityToVO(entity);
  }

  @Override
  public void deleteById(String id) {
    decisionTableMapper.deleteById(id);
  }
}
