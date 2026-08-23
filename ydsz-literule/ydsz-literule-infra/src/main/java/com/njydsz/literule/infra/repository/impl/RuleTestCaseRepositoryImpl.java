package com.njydsz.literule.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.literule.domain.dto.post.RuleTestCasePostDTO;
import com.njydsz.literule.domain.repository.RuleTestCaseRepository;
import com.njydsz.literule.domain.vo.RuleTestCaseVO;
import com.njydsz.literule.infra.converter.LiteruleConverter;
import com.njydsz.literule.infra.entity.RuleTestCaseDO;
import com.njydsz.literule.infra.mapper.RuleTestCaseMapper;

/**
 * 规则测试用例仓储实现（Infra 层）。
 *
 * <p>实现 {@link RuleTestCaseRepository} 接口，封装 {@link RuleTestCaseMapper} 数据访问细节。
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
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class RuleTestCaseRepositoryImpl implements RuleTestCaseRepository {

  private final RuleTestCaseMapper ruleTestCaseMapper;

  private final LiteruleConverter converter = LiteruleConverter.INSTANCE;

  @Override
  public List<RuleTestCaseVO> findByRuleCode(String ruleCode) {
    LambdaQueryWrapper<RuleTestCaseDO> wrapper = new LambdaQueryWrapper<>();
    if (ruleCode != null && !ruleCode.isBlank()) {
      wrapper.eq(RuleTestCaseDO::getRuleCode, ruleCode);
    }
    wrapper.orderByDesc(RuleTestCaseDO::getUpdatedAt);
    List<RuleTestCaseDO> entities = ruleTestCaseMapper.selectList(wrapper);
    return converter.ruleTestCaseListToVO(entities);
  }

  @Override
  public List<RuleTestCaseVO> findAll() {
    List<RuleTestCaseDO> entities = ruleTestCaseMapper.selectList(null);
    return converter.ruleTestCaseListToVO(entities);
  }

  @Override
  public Optional<RuleTestCaseVO> findById(Long id) {
    RuleTestCaseDO entity = ruleTestCaseMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public RuleTestCaseVO save(RuleTestCasePostDTO dto) {
    RuleTestCaseDO entity = converter.postDtoToEntity(dto);
    if (entity.getId() != null) {
      ruleTestCaseMapper.updateById(entity);
    } else {
      ruleTestCaseMapper.insert(entity);
    }
    return converter.entityToVO(entity);
  }

  @Override
  public void deleteById(String id) {
    ruleTestCaseMapper.deleteById(id);
  }
}
