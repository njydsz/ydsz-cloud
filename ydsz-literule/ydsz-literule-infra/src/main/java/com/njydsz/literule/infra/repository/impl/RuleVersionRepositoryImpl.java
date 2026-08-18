package com.njydsz.literule.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.literule.domain.dto.post.RuleVersionSaveDTO;
import com.njydsz.literule.domain.entity.RuleVersionHistory;
import com.njydsz.literule.domain.repository.RuleVersionRepository;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.infra.converter.LiteruleConverter;
import com.njydsz.literule.infra.mapper.RuleVersionHistoryMapper;

/**
 * 规则版本仓储实现（Infra 层）。
 *
 * <p>实现 {@link RuleVersionRepository} 接口，封装 {@link RuleVersionHistoryMapper} 数据访问细节。
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
public class RuleVersionRepositoryImpl implements RuleVersionRepository {

  private final RuleVersionHistoryMapper ruleVersionHistoryMapper;

  private final LiteruleConverter converter = LiteruleConverter.INSTANT;

  @Override
  public void saveVersion(RuleVersionSaveDTO saveDTO) {
    RuleVersionHistory entity = converter.postDtoToEntity(saveDTO);
    ruleVersionHistoryMapper.insert(entity);
  }

  @Override
  public List<RuleVersionVO> listVersions(String ruleCode) {
    LambdaQueryWrapper<RuleVersionHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RuleVersionHistory::getRuleCode, ruleCode)
           .orderByDesc(RuleVersionHistory::getVersion);
    List<RuleVersionHistory> entities = ruleVersionHistoryMapper.selectList(wrapper);
    return converter.ruleVersionListToVO(entities);
  }

  @Override
  public Optional<RuleDefinitionVO> rollback(String ruleCode, int version, String operator) {
    LambdaQueryWrapper<RuleVersionHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RuleVersionHistory::getRuleCode, ruleCode)
           .eq(RuleVersionHistory::getVersion, version);
    RuleVersionHistory targetVersion = ruleVersionHistoryMapper.selectOne(wrapper);
    if (targetVersion == null) {
      return Optional.empty();
    }
    // TODO: 实现回滚逻辑（恢复规则定义 + 保存回滚版本记录）
    throw new UnsupportedOperationException("RuleVersionRepositoryImpl.rollback() 回滚逻辑待实现");
  }
}
