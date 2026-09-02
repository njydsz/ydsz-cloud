package com.njydsz.literule.server.config;

import java.util.List;

import lombok.RequiredArgsConstructor;

import com.njydsz.literule.domain.repository.RuleExecutionTraceRepository;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;

/**
 * 规则执行轨迹查询服务（server 层，P1-12 收口 web 跳层）
 *
 * <p>将 {@link RuleExecutionTraceRepository} 的数据访问收敛到应用服务层，
 * web Controller 不再直接依赖 domain Repository（分层合规），并统一查询上限保护。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@RequiredArgsConstructor
public class RuleTraceQueryService {

  /** 单次查询条数上限 */
  private static final int MAX_QUERY_LIMIT = 5000;

  private final RuleExecutionTraceRepository repository;

  /**
   * 按 traceId 查询执行链路
   *
   * @param traceId 追踪 ID
   * @return 执行轨迹 VO 列表
   */
  public List<RuleExecutionTraceVO> findByTraceId(String traceId) {
    return repository.findByTraceId(traceId);
  }

  /**
   * 按规则编码查询最近执行链路（上限保护）
   *
   * @param ruleCode 规则编码
   * @param limit 最多返回条数（1~5000）
   * @return 执行轨迹 VO 列表
   */
  public List<RuleExecutionTraceVO> findByRuleCode(String ruleCode, int limit) {
    int capped = Math.min(Math.max(1, limit), MAX_QUERY_LIMIT);
    return repository.findByRuleCode(ruleCode, capped);
  }

  /**
   * 查询最近执行链路（上限保护）
   *
   * @param limit 最多返回条数（1~5000）
   * @return 执行轨迹 VO 列表
   */
  public List<RuleExecutionTraceVO> findRecent(int limit) {
    int capped = Math.min(Math.max(1, limit), MAX_QUERY_LIMIT);
    return repository.findRecent(capped);
  }

  /**
   * 按规则编码查询最近 N 条 trace（用于影响分析，上限保护）
   *
   * @param ruleCode 规则编码
   * @param limit 最多返回条数（1~5000）
   * @return 执行轨迹 VO 列表（按 ID 降序）
   */
  public List<RuleExecutionTraceVO> findRecentByRuleCode(String ruleCode, int limit) {
    int capped = Math.min(Math.max(1, limit), MAX_QUERY_LIMIT);
    return repository.findRecentByRuleCode(ruleCode, capped);
  }
}
