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
 * @since 26.10.01
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
    // 转换器刻意忽略主键（@Mapping(target="id", ignore=true)，主键由持久层生成），
    // 这里必须回填 DTO 的 id，否则下面的更新分支恒不可达——"编辑决策表"会退化成
    // 按 table_code 插入重复行。列类型为 varchar(36)，故 Long 主键转字符串写入。
    entity.setId(dto.getId() == null ? null : String.valueOf(dto.getId()));
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
