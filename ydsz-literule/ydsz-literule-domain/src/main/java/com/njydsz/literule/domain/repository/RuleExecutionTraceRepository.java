package com.njydsz.literule.domain.repository;

import java.util.List;

import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;

/**
 * 规则执行轨迹 Repository（domain 层契约）。
 *
 * <p>定义规则执行链路追踪的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link RuleExecutionTraceVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RuleExecutionTraceRepository {

  /**
   * 根据 traceId 查询执行链路（按 ID 升序）。
   *
   * @param traceId 追踪 ID
   * @return 执行轨迹 VO 列表
   */
  List<RuleExecutionTraceVO> findByTraceId(String traceId);

  /**
   * 根据规则编码查询最近执行链路（按 ID 降序）。
   *
   * @param ruleCode 规则编码
   * @param limit 最多返回条数
   * @return 执行轨迹 VO 列表
   */
  List<RuleExecutionTraceVO> findByRuleCode(String ruleCode, int limit);

  /**
   * 查询最近执行链路（按 ID 降序）。
   *
   * @param limit 最多返回条数
   * @return 执行轨迹 VO 列表
   */
  List<RuleExecutionTraceVO> findRecent(int limit);

  /**
   * 根据规则编码查询最近 N 条 trace（用于影响分析）。
   *
   * @param ruleCode 规则编码
   * @param limit 最多返回条数
   * @return 执行轨迹 VO 列表（按 ID 降序）
   */
  List<RuleExecutionTraceVO> findRecentByRuleCode(String ruleCode, int limit);
}
