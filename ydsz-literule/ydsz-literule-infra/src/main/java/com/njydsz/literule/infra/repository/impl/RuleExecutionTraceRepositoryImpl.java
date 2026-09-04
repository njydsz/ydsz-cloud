package com.njydsz.literule.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.literule.domain.repository.RuleExecutionTraceRepository;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.converter.LiteruleConverter;
import com.njydsz.literule.domain.entity.RuleExecutionTrace;
import com.njydsz.literule.infra.mapper.RuleExecutionTraceMapper;

/**
 * 规则执行轨迹仓储实现（Infra 层）。
 *
 * <p>实现 {@link RuleExecutionTraceRepository} 接口，封装 {@link RuleExecutionTraceMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link LiteruleConverter} 将 Entity 转换为 VO 后返回
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class RuleExecutionTraceRepositoryImpl implements RuleExecutionTraceRepository {

  private final RuleExecutionTraceMapper ruleExecutionTraceMapper;

  private final LiteruleConverter converter = LiteruleConverter.INSTANCE;

  @Override
  public List<RuleExecutionTraceVO> findByTraceId(String traceId) {
    List<RuleExecutionTrace> entities =
        ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTrace>()
                .eq(RuleExecutionTrace::getTraceId, traceId)
                .orderByAsc(RuleExecutionTrace::getId));
    return converter.ruleExecutionTraceListToVO(entities);
  }

  @Override
  public List<RuleExecutionTraceVO> findByRuleCode(String ruleCode, int limit) {
    List<RuleExecutionTrace> entities =
        ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTrace>()
                .eq(RuleExecutionTrace::getRuleCode, ruleCode)
                .orderByDesc(RuleExecutionTrace::getId)
                .last("LIMIT " + limit));
    return converter.ruleExecutionTraceListToVO(entities);
  }

  @Override
  public List<RuleExecutionTraceVO> findRecent(int limit) {
    List<RuleExecutionTrace> entities =
        ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTrace>()
                .orderByDesc(RuleExecutionTrace::getId)
                .last("LIMIT " + limit));
    return converter.ruleExecutionTraceListToVO(entities);
  }

  @Override
  public List<RuleExecutionTraceVO> findRecentByRuleCode(String ruleCode, int limit) {
    List<RuleExecutionTrace> entities =
        ruleExecutionTraceMapper.selectList(
            new LambdaQueryWrapper<RuleExecutionTrace>()
                .eq(RuleExecutionTrace::getRuleCode, ruleCode)
                .orderByDesc(RuleExecutionTrace::getId)
                .last("LIMIT " + limit));
    return converter.ruleExecutionTraceListToVO(entities);
  }
}
